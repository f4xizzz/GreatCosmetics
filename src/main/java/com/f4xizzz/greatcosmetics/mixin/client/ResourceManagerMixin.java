package com.f4xizzz.greatcosmetics.mixin.client;

import com.f4xizzz.greatcosmetics.manager.ThemeManager;
import net.minecraft.resource.NamespaceResourceManager;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Mixin(NamespaceResourceManager.class)
public abstract class ResourceManagerMixin {

    @Unique
    private static final boolean GREATCOSMETICS_DEBUG_MODE = true; // Flag de Diagnóstico

    // =========================================================================
    // 1. CARREGAMENTO DIRETO (Imagens isoladas, fundos específicos)
    // =========================================================================
    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void greatcosmetics$interceptGetResource(Identifier id, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (ThemeManager.isLightMode) {
            String path = id.getPath();

            if (!path.startsWith("lightmode/") && path.endsWith(".png")) {
                try {
                    Identifier lightId = Identifier.of(id.getNamespace(), "lightmode/" + path);
                    Optional<Resource> lightResource = ((NamespaceResourceManager)(Object)this).getResource(lightId);

                    if (lightResource.isPresent()) {
                        cir.setReturnValue(lightResource);
                        if (GREATCOSMETICS_DEBUG_MODE) {
                            System.out.println("[GreatCosmetics-Debug] Substituiu (Get): " + id);
                        }
                    }
                } catch (Exception e) {} // Ignora falhas para usar a textura Dark de segurança
            }
        }
    }

    // =========================================================================
    // 2. CARREGAMENTO EM LOTE (O Culpado! Sprites de GUI e Blocos do Minecraft)
    // =========================================================================
    @Inject(method = "findResources", at = @At("RETURN"), cancellable = true)
    private void greatcosmetics$interceptFindResources(String startingPath, Predicate<Identifier> allowedPathPredicate, CallbackInfoReturnable<Map<Identifier, Resource>> cir) {
        if (ThemeManager.isLightMode) {

            // Previne loop infinito ignorando a própria pasta lightmode
            if (!startingPath.startsWith("lightmode/")) {
                try {
                    String lightPath = "lightmode/" + startingPath;

                    // O Minecraft testa se a textura é válida (ex: termina com .png).
                    // Precisamos disfarçar o nome "lightmode/" para ele aceitar o nosso arquivo!
                    Predicate<Identifier> wrappedPredicate = lightId -> {
                        String p = lightId.getPath();
                        if (p.startsWith("lightmode/")) {
                            return allowedPathPredicate.test(Identifier.of(lightId.getNamespace(), p.substring(10)));
                        }
                        return allowedPathPredicate.test(lightId);
                    };

                    // Pede ao Minecraft para caçar tudo que está na nossa pasta lightmode/
                    Map<Identifier, Resource> lightMap = ((NamespaceResourceManager)(Object)this).findResources(lightPath, wrappedPredicate);

                    if (!lightMap.isEmpty()) {
                        // Cria um novo mapa juntando os originais e substituindo pelos nossos!
                        Map<Identifier, Resource> newMap = new HashMap<>(cir.getReturnValue());

                        for (Map.Entry<Identifier, Resource> entry : lightMap.entrySet()) {
                            Identifier lightId = entry.getKey();

                            // Tira a palavra "lightmode/" do caminho para enganar o Minecraft
                            String normalPath = lightId.getPath().substring(10);
                            Identifier normalId = Identifier.of(lightId.getNamespace(), normalPath);

                            newMap.put(normalId, entry.getValue());

                            if (GREATCOSMETICS_DEBUG_MODE) {
                                System.out.println("[GreatCosmetics-Debug] Substituiu (Find): " + normalId);
                            }
                        }

                        cir.setReturnValue(newMap);
                    }
                } catch (Exception e) {
                    if (GREATCOSMETICS_DEBUG_MODE) {
                        System.err.println("[GreatCosmetics-Debug] Erro ao injetar lote de imagens!");
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    // =========================================================================
    // 3. CARREGAMENTO MÚLTIPLO (Camadas de Textura)
    // =========================================================================
    @Inject(method = "getAllResources", at = @At("HEAD"), cancellable = true)
    private void greatcosmetics$interceptGetAllResources(Identifier id, CallbackInfoReturnable<List<Resource>> cir) {
        if (ThemeManager.isLightMode) {
            String path = id.getPath();

            if (!path.startsWith("lightmode/") && path.endsWith(".png")) {
                try {
                    Identifier lightId = Identifier.of(id.getNamespace(), "lightmode/" + path);
                    List<Resource> lightResources = ((NamespaceResourceManager)(Object)this).getAllResources(lightId);

                    if (!lightResources.isEmpty()) {
                        cir.setReturnValue(lightResources);
                        if (GREATCOSMETICS_DEBUG_MODE) {
                            System.out.println("[GreatCosmetics-Debug] Substituiu (GetAll): " + id);
                        }
                    }
                } catch (Exception e) {}
            }
        }
    }
}