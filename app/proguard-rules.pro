# kotlinx.serialization: keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class app.screenbalance.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.screenbalance.**$$serializer { *; }
# The accessibility service is referenced from the manifest only.
-keep class app.screenbalance.service.FocusAccessibilityService { *; }
