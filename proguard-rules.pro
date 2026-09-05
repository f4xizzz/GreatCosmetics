# ProGuard — GreatCosmetics (roda DEPOIS do remapJar; classes já em mapeamento intermediary).
# Só ofuscação de nome + strip de debug. Sem shrink/optimize (Fabric + reflexão + records).

-dontshrink
-dontoptimize
-dontwarn **
-ignorewarnings
-allowaccessmodification
-renamesourcefileattribute SourceFile

# Mantém o necessário pra runtime/records/anotações; TIRA SourceFile + LineNumberTable
# (stack trace fica sem linha — é o preço do anti-decompile).
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,EnclosingMethod,*Annotation*,Record,PermittedSubclasses

# NÃO reempacota E mantém os NOMES de pacote: o GreatCosmeticsMixinPlugin faz denylist por
# PREFIXO de pacote (com.f4xizzz.greatcosmetics.security. / .command. / .core.). Sem isto o
# ProGuard renomeia "command" -> "a" e o match por prefixo para de valer. (As CLASSES continuam
# virando a/b/c dentro do pacote — só a estrutura de pastas fica visível.)
-keeppackagenames com.f4xizzz.greatcosmetics.**

# ── Entrypoints (fabric.mod.json) ──
-keep class com.f4xizzz.greatcosmetics.GreatCosmetics { *; }
-keep class com.f4xizzz.greatcosmetics.GreatCosmeticsClient { *; }

# ── Mixin (engine casa por nome) ──
-keep class com.f4xizzz.greatcosmetics.mixin.** { *; }
-keep class com.f4xizzz.greatcosmetics.security.GreatCosmeticsMixinPlugin { *; }

# ── Rede: records + CODEC + IDs (viajam entre client/server; registro reflexivo) ──
-keep class com.f4xizzz.greatcosmetics.network.** { *; }

# ── GeckoLib: itens/modelos/renderers referenciados por registro ──
-keep class com.f4xizzz.greatcosmetics.geckolib.** { *; }

# ── Models de config (Gson: nome do campo == chave no JSON) ──
-keep class com.f4xizzz.greatcosmetics.config.** { *; }

# ── Todo o client-side (o jar client carrega isto; DevPage é achado via Class.forName por String) ──
-keep class com.f4xizzz.greatcosmetics.client.** { *; }

# ── Alvos exatos do denylist do MixinPlugin (mantém o NOME p/ o match casar) ──
-keep class com.f4xizzz.greatcosmetics.util.WardrobeManager { *; }

# ── Genéricos seguros ──
-keepclassmembers class * extends java.lang.Record { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * {
    @org.spongepowered.asm.mixin.* <methods>;
}

# TODO O RESTO — security.ActivationManager (membros), util.*, database.*, command.* (membros),
# manager.*, integration.* — vira a.a / a.b no mesmo pacote.
