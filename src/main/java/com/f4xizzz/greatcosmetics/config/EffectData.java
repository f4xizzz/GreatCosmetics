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
}