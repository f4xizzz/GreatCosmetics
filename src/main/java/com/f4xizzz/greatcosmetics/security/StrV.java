package com.f4xizzz.greatcosmetics.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Guarda algumas strings sensiveis (chaves RSA, nome do manifesto de integridade) codificadas —
 *  nao e criptografia forte (a chave de decodificacao TEM que estar do lado, e so ofuscacao), so
 *  evita que um "strings"/grep no jar entregue de bandeja o que cada constante e. Gerado — ver
 *  gen_vault.py no repo de ferramentas internas; nao editar os arrays na mao. */
final class StrV {
    private StrV() {}

    private static final byte[] K = {
            107, -38, 114, -78, -7, -13, 79, -71, 27, 59, -14, 114, 94, -17, -81, -66, -66, 125, 63, 81, 91, -12, 114, 117
    };

    private static final String[] E = {
            "JpM78LCZDvdZXJkDNoTG+YcKDxMapTczKps98biid/hWcrswHYjk/f8sehApoj04W5Rdh5HHZPV6EJAwB7rb7cc6UgNqxRhaHIsbyJCJBsBZcbMoOdr75PEpVSUPmTAUG4M70JCkHf1IU6MKJMT6+NkPTCUOjUYdDbMe3Iy1JPtRWrUbE5/9/dA1TWcIjkANA7w/gciUGepudIgZM4nYxsY5ZilqgyUzEqoE3J7HGeBJC4sxG9/mi8oRVhYBh0YXLuga/YmmIfJcdosEGNjl/McsWxc3xUMNCZQE3cq0PvttC5lED7jtztZPDCk+3zAlD/EK14PBFeMrUaU9FpiZ2IsOSCUDrTkDWIgxgYy/Nv9rU6MEaNrZ3dYuUwVuvQckAJE3gJGXDO9TbcA4ZqvW3NIJCCgxhQo4AYgcxYq1ffxjX5sQadvu7vAeeAYBnBYtJo0f+bGhINRCa6YdMrXsjYYqRiAOsAIMBZNG3taBB8l5cIUINaTIxu0JZgZqnh40Pos79riiDvs=",
            "JpM78LCZDvdZXJkDNoTG+YcKDxMapTczKps98biid/hWcrswHYjk/f8sehApxwQCAeoF/bTYdspZQoYQEbmcyMsUZiss3zYwG+0n9YvcLtduYYIXDo7HjY8WfTAfukoSBLYm8LCGPpZPXLsVDojVld1PewYSmQMvMuMw/82bZOpWD7tDbtvC7OoICTsSxgFaB40T/5KSBd9+Y5s4KqiXhoxWRzY4kQBaX+0h9La1ZOBIa6dHHKjJ6Is5CjA+uBETMY84+oGyeJJ0TrYRa4abickYTRg+tTodBIgd+q6hKP91c6gaJKr/9/AWcxBsw0UAKI9ZiqqqDchBc90EDqHDho4+EBg8khA8EpI+15SBOfFXXYEAKK3b9u47C346gkBMHo4F1aihLM4sCpodHbqd79MVDj01nxkZIeso5JyfHNcsaJcDHI7szfk2FDxpowcXP71G3rejJ9FuepAea7/i7McLSmBrkAseI4whx4rBGNtyeIM5apvV5tYZaB4L2xQxP4s79riiDvs=",
            "Jp8m89S6Af80XIAXP5vM0c0QWiUylwFbGLMV"
    };

    static String s(int i) {
        byte[] raw = Base64.getDecoder().decode(E[i]);
        byte[] out = new byte[raw.length];
        for (int j = 0; j < raw.length; j++) out[j] = (byte) (raw[j] ^ K[j % K.length]);
        return new String(out, StandardCharsets.UTF_8);
    }
}
