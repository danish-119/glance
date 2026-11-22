package app.screenbalance.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.screenbalance.engine.BlockReason
import app.screenbalance.model.ThemePreference
import app.screenbalance.ui.intervention.InterventionActions
import app.screenbalance.ui.intervention.InterventionScreen
import app.screenbalance.ui.intervention.InterventionState
import app.screenbalance.ui.theme.GlanceTheme

/**
 * Hosts the intervention UI in a TYPE_ACCESSIBILITY_OVERLAY window. Accessibility overlays need
 * no extra permission and are not subject to background-activity-start restrictions.
 */
internal class InterventionOverlay(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var owner: OverlayOwner? = null

    val isShowing: Boolean get() = root != null
    /** A blank cover is up (it lifts by itself once the app underneath has been moved). */
    val isCover: Boolean get() = root != null && shown?.coverOnly == true
    private var shown: InterventionState? = null

    private val audio = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    /**
     * Held while a cover hides a playing video, so the video underneath falls silent at once
     * instead of being heard while the app is moved off it. Transient: music from other apps
     * resumes as soon as the cover goes.
     */
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setOnAudioFocusChangeListener { }
        .build()
    private var holdingFocus = false

    fun show(state: InterventionState, theme: ThemePreference, actions: InterventionActions) {
        dismiss()
        val o = OverlayOwner().also { it.start() }
        val compose = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { GlanceTheme(theme) { InterventionScreen(state, actions) } }
        }
        val container = BackInterceptingLayout(service) { actions.onDismiss() }.apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            addView(compose, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Not focusable: back presses go to Instagram underneath, which the service relies on to
            // close the Reels player while the overlay is already covering it. Touches still land here.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            title = "Glance intervention"
        }
        try {
            wm.addView(container, params)
            root = container
            owner = o
            shown = state
            if (state.pauseAudio && state.reason in SILENCE_FOR && !(state.coverOnly && state.reason == BlockReason.EXPLORE)) {
                holdingFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: RuntimeException) {
            // WindowManager.BadTokenException etc.: the service is being torn down.
            Log.w(TAG, "Could not show intervention overlay", e)
            o.destroy()
        }
    }

    fun dismiss() {
        if (holdingFocus) {
            audio.abandonAudioFocusRequest(focus)
            holdingFocus = false
        }
        val r = root ?: return
        root = null
        shown = null
        try {
            wm.removeViewImmediate(r)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Overlay already removed", e)
        }
        owner?.destroy()
        owner = null
    }


    @SuppressLint("ViewConstructor")
    private class BackInterceptingLayout(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
        init {
            isFocusableInTouchMode = true
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    /** Minimal lifecycle + saved-state owner so Compose can run outside an Activity. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            if (registry.currentState != Lifecycle.State.DESTROYED &&
                registry.currentState != Lifecycle.State.INITIALIZED
            ) {
                registry.currentState = Lifecycle.State.DESTROYED
            }
        }
    }

    private companion object {
        const val TAG = "InterventionOverlay"
        /** Blocks that can sit on top of a playing video. */
        val SILENCE_FOR = setOf(BlockReason.REELS, BlockReason.HOME_FEED, BlockReason.EXPLORE, BlockReason.SHORT_VIDEO, BlockReason.OPEN_PAUSE)
    }
}
