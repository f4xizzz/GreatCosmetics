package com.f4xizzz.greatcosmetics.mixin.client;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;

/** Faz o nick de espectadores parar de "pular" de lugar na tab list, de ficar com opacidade
 *  reduzida e de ficar em itálico. Tudo isso é comportamento vanilla do PlayerListHud (confirmado
 *  lendo o bytecode da classe, já que o Yarn não expõe fonte decompilada): o comparador estático
 *  ENTRY_ORDERING usa "é espectador?" como PRIMEIRO critério de ordenação (manda pro final da
 *  lista), o método render() troca a cor do nome pra 0x90FFFFFF (branco com ~56% de alpha), e
 *  applyGameModeFormatting() aplica itálico — as duas últimas só pra quem tá em espectador. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    // Mesmo comparador do vanilla (time -> nome, case-insensitive), só sem o critério de
    // gamemode que empurra espectador pro fim da lista — ver greatcosmetics$stableOrdering.
    private static final Comparator<PlayerListEntry> GREATCOSMETICS_STABLE_ORDERING =
            Comparator.<PlayerListEntry, String>comparing(entry -> {
                        Team team = entry.getScoreboardTeam();
                        return team != null ? team.getName() : "";
                    })
                    .thenComparing(entry -> entry.getProfile().getName(), String::compareToIgnoreCase);

    @Redirect(method = "collectPlayerEntries", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/hud/PlayerListHud;ENTRY_ORDERING:Ljava/util/Comparator;"))
    private Comparator<PlayerListEntry> greatcosmetics$stableOrdering() {
        return GREATCOSMETICS_STABLE_ORDERING;
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = -1862270977))
    private int greatcosmetics$fullOpacityForSpectators(int original) {
        return -1;
    }

    @Inject(method = "applyGameModeFormatting", at = @At("HEAD"), cancellable = true)
    private void greatcosmetics$noItalicsForSpectators(PlayerListEntry entry, MutableText name, CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(name);
    }
}
