package com.f4xizzz.greatcosmetics.config;

public class EffectData {
    public String particleId;  // Ex: "minecraft:flame", "minecraft:end_rod"
    public int count;          // Quantidade de partículas por vez
    public double speed;       // Velocidade da partícula (se ela "explode" pra longe ou fica parada)

    // Spread = O quanto a partícula se espalha no eixo X, Y e Z
    public double spreadX;
    public double spreadY;
    public double spreadZ;

    // Offset = Onde a partícula nasce em relação ao jogador
    // (0 = no pé, 1.0 = na barriga, 2.0 = na cabeça)
    public double offsetX = 0.0;
    public double offsetY = 0.0; // Essa você já tem!
    public double offsetZ = 0.0;

    // Intervalo de tempo: De quantos em quantos Ticks a partícula aparece (20 Ticks = 1 segundo)
    public int tickInterval = 10;

    // Cor RGB (0-255 cada). Os TRÊS >= 0 = cor ligada; -1 = sem cor. Só tem efeito em partículas
    // coloríveis: minecraft:dust, minecraft:dust_color_transition, minecraft:entity_effect
    // (ver util/ParticleFx). Nas outras a cor é ignorada.
    public int colorR = -1;
    public int colorG = -1;
    public int colorB = -1;

    public boolean hasColor() {
        return colorR >= 0 && colorG >= 0 && colorB >= 0;
    }

    // ==========================================
    // FORMAS DE PARTÍCULA (ver util/ParticleShapes) — SIMPLE = comportamento de sempre
    // (count partículas com spread, a cada tickInterval).
    // ==========================================
    public String shape = "SIMPLE";      // SIMPLE | CIRCLE | HELIX | BEAM | PULSE

    // comuns às formas (SIMPLE ignora):
    public double radius = 1.0;
    public int points = 40;              // partículas por volta / por anel
    public int strands = 1;              // cópias paralelas (hélices, anéis)
    public double phase = 0.0;           // ângulo inicial, graus
    public boolean clockwise = false;
    public double rotX = 0.0, rotY = 0.0, rotZ = 0.0;   // rotação da forma inteira, graus

    // HELIX:
    public double helixHeight = 2.0;
    public double turns = 1.0;
    public boolean reverse = false;

    // BEAM:
    public double beamHeight = 4.0;
    public double spacing = 0.15;
    public boolean upwards = true;

    // PULSE:
    public double endRadius = 3.0;
    public int endPoints = 50;
    public int rings = 10;
    public boolean outwards = true;

    // ANIMAÇÃO: 0 = estática (forma inteira a cada tickInterval). >0 = anima ao longo de animTicks
    // (progresso 0→1), pausa tickInterval, repete.
    public int animTicks = 0;

    public boolean isShape() {
        return shape != null && !shape.equalsIgnoreCase("SIMPLE");
    }
}