package com.f4xizzz.greatcosmetics.config;

public class PokemonSkin {
    private String id;
    private String displayName;
    private String species;
    private String aspect;
    private int cooldownMinutes;

    /** Formas alternativas dessa skin (mega, gmax, ash, regional...), cada uma como um aspect do
     *  Cobblemon (ex: "mega", "gmax") — definidas na mão pelo admin no pokeskins.json, já que nem
     *  toda forma que o Cobblemon conhece pra uma espécie necessariamente faz sentido pra essa
     *  skin específica. O botão de forma no preview da Party page só aparece quando essa lista
     *  não está vazia, e cicla injetando/removendo o aspect de cada uma no Pokémon do preview. */
    private java.util.List<String> altForms = new java.util.ArrayList<>();

    /** Grupo temático dessa skin (ex: "OverWatch", "Arcane", "League Of Legends"), usado pra
     *  agrupar visualmente skins relacionadas na lista da Party page. Vazio = sem grupo. */
    private String group = "";

    public PokemonSkin(String id, String displayName, String species, String aspect, int cooldownMinutes) {
        this.id = id;
        this.displayName = displayName;
        this.species = species;
        this.aspect = aspect;
        this.cooldownMinutes = cooldownMinutes;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getSpecies() { return species; }
    public String getAspect() { return aspect; }
    public int getCooldownMinutes() { return cooldownMinutes; }

    public java.util.List<String> getAltForms() {
        return altForms != null ? altForms : java.util.List.of();
    }

    public void setAltForms(java.util.List<String> altForms) {
        this.altForms = altForms != null ? altForms : new java.util.ArrayList<>();
    }

    public String getGroup() {
        return group != null ? group : "";
    }

    public void setGroup(String group) {
        this.group = group != null ? group : "";
    }
}