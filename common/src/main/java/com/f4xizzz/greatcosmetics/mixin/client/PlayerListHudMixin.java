package com.f4xizzz.greatcosmetics.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;

/** Faz o nick de espectadores parar de "pular" de lugar na tab list, de ficar com opacidade
 *  reduzida e de ficar em itálico. Tudo isso é comportamento vanilla do PlayerListHud (confirmado
 *  lendo o bytecode da classe, já que o Yarn não expõe fonte decompilada): o comparador estático
 *  ENTRY_ORDERING usa "é espectador?" como PRIMEIRO critério de ordenação (manda pro final da
 *  lista), o método render() troca a cor do nome pra 0x90FFFFFF (branco com ~56% de alpha), e
 *  applyGameModeFormatting() aplica itálico — as duas últimas só pra quem tá em espectador. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {

    // Mesmo comparador do vanilla (time -> nome, case-insensitive), só sem o critério de
    // gamemode que empurra espectador pro fim da lista — ver greatcosmetics$stableOrdering.
    private static final Comparator<PlayerInfo> GREATCOSMETICS_STABLE_ORDERING =
            Comparator.<PlayerInfo, String>comparing(entry -> {
                        PlayerTeam team = entry.getTeam();
                        return team != null ? team.getName() : "";
                    })
                    .thenComparing(entry -> entry.getProfile().getName(), String::compareToIgnoreCase);

    @Redirect(method = "getPlayerInfos", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;PLAYER_COMPARATOR:Ljava/util/Comparator;"))
    private Comparator<PlayerInfo> greatcosmetics$stableOrdering() {
        return GREATCOSMETICS_STABLE_ORDERING;
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = -1862270977))
    private int greatcosmetics$fullOpacityForSpectators(int original) {
        return -1;
    }

    @Inject(method = "decorateName", at = @At("HEAD"), cancellable = true)
    private void greatcosmetics$noItalicsForSpectators(PlayerInfo entry, MutableComponent name, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(name);
    }
}
