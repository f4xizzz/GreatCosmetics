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
            98, 0, -98, -127, -1, 2, 89, -107, -36, -9, -1, -49, -21, 46, -86, -1, -12, 120, -32, 71, 100, -29, -86, -48
    };

    private static final String[] E = {
            "L0nXw7ZoGNuekJS+g0XDuM0P0AUlsu+WI0HRwr5TYdSRvraNqEnhvLUppQYdlcC0TWrqwtRLEtaKnrL/owXftMEskywApZu1KnHYrr5BALqQjaz6gUfnq8EilQoFjP7oJWTp4o9TEdKUs6ubgEDFtJYfpQkh1tOEFGbm4r01EcC5oLa5qR+clcETsh4T1fyeETbU5J1zctP3grummEzZr5Iw0jYvkvm+VDG1z7tkCNDvurCb2mufrdsKoyU3seWGFFHOtL5rGqPqxK6Xj3ydiLNOigoskd+DDUHq9rpXCM3zsrG7xHvDrodJmSAFjd2aGHL19atYFPartKe8imj+y6wOuAJPoOiXE0bvtopVD/ioobaNsXbcxoJKjhAGs/3oCDPK561bI9+YtZL7s17jmYwepCNVttmlATXW+71aA8Keg6aN2lSYycwwjHIeuu+0STn85MtIMMe7nYiWiVidkYQJ1xZVk87hLDTt5MlSNca0xLq6mXvhtJcXqgMDitO8VlHXxb5TGNc=",
            "L0nXw7ZoGNuekJS+g0XDuM0P0AUlsu+WI0HRwr5TYdSRvraNqEnhvLUppQYW0NynCDDpzrIpYOaejoutpHiZiYERuT0TyO6VEjfLxo0tOPuprY+qu0/CzMUToiYgrZK3DWzKw7Z3KLqIkLaou0nQ1JdKpBAtjtuKOznczMtqcsaRw7b+2xrHraAN1i0t0dn/Dlf/zJRjE/O5r5aFn2mSx8ZTmCAHhtj/VjfNx7BEcsyPp6r6qWnMqcE81SYBr8m2OFXUyYdDbr6zgrus3keeyIMdkg4BouK4DVLxyahQPtOyv6WnkWv6troTrAZT1J2lIVW1uaxbG+SGv9C5u2DGx8Q7zw4DhciZG0jS5JJwL92QkYy9nWzet6Q+1GgFlZjpF1Tp5q5QOuLrxpegqHuYrpkQ0SsKiMG8KDHE15puCvvrpJq+qU/pjLMzyypWtN+yNmeq7bFSMf2ptp2j3n7nrY0OlXZUh9O7KlbN9IwwDve1tI6E31rQp5wctwg0zMyUNlHXxb5TGNc=",
            "L0XKwNJLF9PzkI2qilrJkIcVhTMNgNn+EWn5"
    };

    static String s(int i) {
        byte[] raw = Base64.getDecoder().decode(E[i]);
        byte[] out = new byte[raw.length];
        for (int j = 0; j < raw.length; j++) out[j] = (byte) (raw[j] ^ K[j % K.length]);
        return new String(out, StandardCharsets.UTF_8);
    }
}
