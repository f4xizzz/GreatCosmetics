package com.f4xizzz.greatcosmetics.util;

/**
 * Um "cosmético equipado" é guardado como uma String só, em toda camada (DB, payloads, caches).
 * Pra suportar VARIANTES sem mudar nenhum tipo, a variante é codificada na própria string:
 * {@code "baseId#variantId"}. Sem {@code #} = "Padrão" (comportamento de sempre).
 *
 * <p>Ids de cosmético são {@code [a-z0-9_]} (lowercased por {@code equipCosmetic}/{@code getCosmeticById}),
 * então {@code #} nunca aparece num id legítimo. Esta classe é o único lugar que faz o split.
 */
public final class EquippedCosmeticId {

    public static final char SEP = '#';

    private EquippedCosmeticId() {}

    /** {@code "hat#red"} → {@code "hat"}; {@code "hat"} → {@code "hat"}; {@code null} → {@code null}. Não mexe no case. */
    public static String base(String equipped) {
        if (equipped == null) return null;
        int i = equipped.indexOf(SEP);
        return i < 0 ? equipped : equipped.substring(0, i);
    }

    /** {@code "hat#red"} → {@code "red"}; {@code "hat"} → {@code ""}. */
    public static String variant(String equipped) {
        if (equipped == null) return "";
        int i = equipped.indexOf(SEP);
        return i < 0 ? "" : equipped.substring(i + 1);
    }

    /** {@code ("Hat","Red")} → {@code "hat#red"}; variante vazia → só o base. Lowercase os dois. */
    public static String compose(String baseId, String variantId) {
        String b = baseId == null ? "" : baseId.toLowerCase();
        if (variantId == null || variantId.isBlank()) return b;
        return b + SEP + variantId.toLowerCase();
    }

    public static boolean hasVariant(String equipped) {
        return equipped != null && equipped.indexOf(SEP) >= 0;
    }
}
