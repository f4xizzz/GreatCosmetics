package com.f4xizzz.greatcosmetics.security;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Plugin de mixin do GreatCosmetics (registrado como {@code "plugin"} no
 * {@code greatcosmetics.mixins.json}).
 *
 * <p>Recusa — de QUALQUER mod, inclusive este — qualquer Mixin que tente alvejar as classes de
 * licença / enforcement. Sem isso, um crack poderia sobrescrever a checagem de ativação por fora.
 * A recusa é silenciosa de propósito (não loga o nome da classe): não dá pista pra quem tenta.
 */
public class GreatCosmeticsMixinPlugin implements IMixinConfigPlugin {

    // Prefixos de pacote — o ProGuard NÃO reempacota o mod, então o nome do pacote sobrevive à
    // ofuscação e o match por prefixo continua valendo mesmo com a classe renomeada.
    private static final String[] PROTECTED_PREFIXES = {
            "com.f4xizzz.greatcosmetics.security.",
            "com.f4xizzz.greatcosmetics.command.",
            "com.f4xizzz.greatcosmetics.core.",            // reservado (Fase 2)
    };

    private static final String[] PROTECTED_EXACT = {
            "com.f4xizzz.greatcosmetics.GreatCosmetics",
            "com.f4xizzz.greatcosmetics.util.WardrobeManager",
    };

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (targetClassName == null) return true;
        for (String p : PROTECTED_PREFIXES) {
            if (targetClassName.startsWith(p)) return false;
        }
        for (String c : PROTECTED_EXACT) {
            if (targetClassName.equals(c)) return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
