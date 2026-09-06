# ProGuard — GreatCosmetics multiloader (roda DEPOIS do remapJar de cada plataforma).
# Só ofuscação de nome + strip de debug. Sem shrink/optimize (Fabric/NeoForge + reflexão + records
# + Mixin + GeckoLib + Gson + Architectury).

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
-keeppackagenames architectury_inject_**

# ── Classes injetadas pelo Architectury Transformer (referenciadas por nome internamente) ──
-keep class architectury_inject_** { *; }

# ── Entrypoints por plataforma (fabric.mod.json / neoforge.mods.toml) ──
-keep class com.f4xizzz.greatcosmetics.GreatCosmetics { *; }
-keep class com.f4xizzz.greatcosmetics.GreatCosmeticsClient { *; }
-keep class com.f4xizzz.greatcosmetics.neoforge.** { *; }

# ── Entrypoints comuns (referenciados pelos stubs de plataforma + hooks + mixins) ──
-keep class com.f4xizzz.greatcosmetics.GreatCosmeticsCommon { *; }
-keep class com.f4xizzz.greatcosmetics.GreatCosmeticsServer { *; }
-keep class com.f4xizzz.greatcosmetics.GreatCosmeticsClientInit { *; }
-keep class com.f4xizzz.greatcosmetics.GcServer { *; }
-keep class com.f4xizzz.greatcosmetics.platform.** { *; }

# ── Mixin (engine casa por nome — classes E membros dos handlers referenciados pelo refmap) ──
-keep class com.f4xizzz.greatcosmetics.mixin.** { *; }
-keep class com.f4xizzz.greatcosmetics.security.GreatCosmeticsMixinPlugin { *; }

# ── Rede: records + CODEC + IDs (viajam entre client/server; registro reflexivo) ──
-keep class com.f4xizzz.greatcosmetics.network.** { *; }

# ── GeckoLib: itens/modelos/renderers referenciados por registro ──
-keep class com.f4xizzz.greatcosmetics.geckolib.** { *; }

# ── Models de config (Gson: nome do campo == chave no JSON) ──
-keep class com.f4xizzz.greatcosmetics.config.** { *; }

# ── Todo o client-side (DevPage é achado via Class.forName por String; GUI + caches) ──
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
# manager.* — vira a.a / a.b no mesmo pacote.
