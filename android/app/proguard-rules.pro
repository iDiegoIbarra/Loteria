# Modelos serializados con kotlinx.serialization (índice de cartas en JSON).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.diegoibarra.loteria.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
