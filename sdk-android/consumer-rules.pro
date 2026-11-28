# R8 / ProGuard rules for apps that use io.composecast:sdk-android with minify enabled (T-058).
# Packaged into the AAR (proguard.txt) and applied to the consuming app automatically.

# Ktor's body<BrandConfig>() / body<UpgradeRequiredResponse>() look the serializer up from the
# type at runtime (serializer(KType) -> the companion's serializer() by reflection).
# kotlinx-serialization ships generic rules for this; these keep the protocol models explicitly so
# a stricter app configuration (or an older serialization version) cannot strip them.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature
-if @kotlinx.serialization.Serializable class io.composecast.protocol.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class io.composecast.protocol.** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class io.composecast.protocol.** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Remote Compose player (remote-core / remote-player-core / remote-player-view 1.0.0-alpha23): no
# keep rules needed. Verified: none of its classes uses reflection (no java/lang/reflect,
# Class.forName or ServiceLoader), and it ships no rules of its own. Re-check on RC upgrades
# (docs/remote-compose.md).
