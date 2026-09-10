package com.f4xizzz.greatcosmetics.integration;

import com.f4xizzz.greatcosmetics.client.gui.Wardrobe3DScreen;
import com.f4xizzz.greatcosmetics.client.gui.pages.PartyPage;
import com.f4xizzz.greatcosmetics.client.gui.pages.WardrobePage;

/**
 * SOFT-DEP COBBLEMON (client): ponte carregada preguiçosamente para instanciar a
 * {@link PartyPage} (que toca {@code com.cobblemon.*} nas assinaturas — referenciá-la de uma
 * classe sempre-carregada explodiria com NoClassDefFoundError num cliente sem Cobblemon).
 * {@link Wardrobe3DScreen#init()} só chama isto atrás de {@code ModCompat.cobblemon()}, então
 * esta classe (e a {@code PartyPage}) só carregam quando o Cobblemon está presente.
 */
public final class CobblemonClient {

    private CobblemonClient() {}

    public static WardrobePage newPartyPage(Wardrobe3DScreen screen) {
        return new PartyPage(screen);
    }
}
