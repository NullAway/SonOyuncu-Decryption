package com.deluxe.sonoyuncu.decrypt;
public final class AesDecryptor {
    private static final int NR = 14;
    private static final String RK240_HEX =
            "71a1a878e7aa9a00f39b0e8d85115b3633881a121c883381eb57f1a763af2737"
          + "52365d96c2c8d30ecc7a34bef92ffe3a6ffa22542f002993f7dfc22688f8d690"
          + "3503e5f490fe8e980eb2e7b03555ca842bcfcef440fa0bc7d8dfebb57f2714b6"
          + "0056475da5fd6b6c9e4c69283be72d3484ca895f6b35c5339825e072a7f8ff03"
          + "63a4dcc7a5ab2c313bb10244a5ab441cb59dc81befff4c6cf31025413fdd1f71"
          + "7eb83322c60ff0f69e1a2e759e1a465894bf9db55a6284771cef692dcccd3a30"
          + "6b5a6daab8b7c3d45815de830000682ddd3c8404cedd19c2468ded5ad022531d"
          + "239b1a1f7c7fc62b9d2e70cbfadabecb";
    private static final byte[] IV = hex("bcfc2e99ea3189bccef69f549d8ddbc8");
    private static final byte[][] RKS = new byte[NR + 1][16];
    private static final int[] INV_SBOX = new int[256];
    private static final int[] M9 = new int[256];
    private static final int[] M11 = new int[256];
    private static final int[] M13 = new int[256];
    private static final int[] M14 = new int[256];
    private AesDecryptor() { }
    static {
        byte[] rk240 = hex(RK240_HEX);
        for (int r = 0; r <= NR; r++) {
            for (int w = 0; w < 4; w++) {
                for (int j = 0; j < 4; j++) {
                    RKS[r][4 * w + j] = rk240[16 * r + 4 * w + (3 - j)];
                }
            }
        }
        int[] sbox = new int[256];
        int p = 1, q = 1;
        while (true) {
            p = p ^ ((p << 1) & 0xFF) ^ ((p & 0x80) != 0 ? 0x1B : 0);
            q ^= q << 1; q ^= q << 2; q ^= q << 4; q &= 0xFF;
            if ((q & 0x80) != 0) q ^= 0x09;
            int xf = q ^ rotl(q, 1) ^ rotl(q, 2) ^ rotl(q, 3) ^ rotl(q, 4);
            sbox[p] = (xf ^ 0x63) & 0xFF;
            if (p == 1) break;
        }
        sbox[0] = 0x63;
        for (int i = 0; i < 256; i++) INV_SBOX[sbox[i]] = i;
        for (int x = 0; x < 256; x++) {
            M9[x] = gmul(x, 9);
            M11[x] = gmul(x, 11);
            M13[x] = gmul(x, 13);
            M14[x] = gmul(x, 14);
        }
    }
    private static int rotl(int x, int s) {
        return ((x << s) | (x >>> (8 - s))) & 0xFF;
    }
    private static int gmul(int a, int b) {
        int r = 0;
        for (int i = 0; i < 8; i++) {
            if ((b & 1) != 0) r ^= a;
            int hi = a & 0x80;
            a = (a << 1) & 0xFF;
            if (hi != 0) a ^= 0x1B;
            b >>= 1;
        }
        return r;
    }
    public static byte[] decrypt(byte[] ct) {
        int n16 = ct.length - (ct.length % 16);
        byte[] out = new byte[ct.length];
        byte[] prev = IV.clone();
        byte[] s = new byte[16];
        byte[] sub = new byte[16];
        byte[] shift = new byte[16];
        for (int off = 0; off < n16; off += 16) {
            for (int i = 0; i < 16; i++) s[i] = (byte) (ct[off + i] ^ RKS[0][i]);
            for (int rnd = 1; rnd <= NR - 1; rnd++) {
                invSubShift(s, sub, shift);
                invMixColumns(shift, s, RKS[rnd]);
            }
            invSubShift(s, sub, shift);
            for (int i = 0; i < 16; i++) {
                byte d = (byte) (shift[i] ^ RKS[NR][i]);
                out[off + i] = (byte) (d ^ prev[i]);
            }
            System.arraycopy(ct, off, prev, 0, 16);
        }
        if (n16 < ct.length) {
            System.arraycopy(ct, n16, out, n16, ct.length - n16);
        }
        return out;
    }
    private static void invSubShift(byte[] s, byte[] sub, byte[] shift) {
        for (int i = 0; i < 16; i++) sub[i] = (byte) INV_SBOX[s[i] & 0xFF];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                shift[4 * ((c + r) % 4) + r] = sub[4 * c + r];
            }
        }
    }
    private static void invMixColumns(byte[] shift, byte[] s, byte[] rk) {
        for (int c = 0; c < 4; c++) {
            int a0 = shift[4 * c] & 0xFF, a1 = shift[4 * c + 1] & 0xFF;
            int a2 = shift[4 * c + 2] & 0xFF, a3 = shift[4 * c + 3] & 0xFF;
            s[4 * c + 0] = (byte) (M14[a0] ^ M11[a1] ^ M13[a2] ^ M9[a3] ^ rk[4 * c + 0]);
            s[4 * c + 1] = (byte) (M9[a0] ^ M14[a1] ^ M11[a2] ^ M13[a3] ^ rk[4 * c + 1]);
            s[4 * c + 2] = (byte) (M13[a0] ^ M9[a1] ^ M14[a2] ^ M11[a3] ^ rk[4 * c + 2]);
            s[4 * c + 3] = (byte) (M11[a0] ^ M13[a1] ^ M9[a2] ^ M14[a3] ^ rk[4 * c + 3]);
        }
    }
    public static byte[] unpad(byte[] data) {
        if (data == null || data.length == 0) return data;
        int p = data[data.length - 1] & 0xFF;
        if (p >= 1 && p <= 16 && data.length >= p) {
            boolean ok = true;
            for (int i = data.length - p; i < data.length; i++) {
                if ((data[i] & 0xFF) != p) { ok = false; break; }
            }
            if (ok) {
                byte[] out = new byte[data.length - p];
                System.arraycopy(data, 0, out, 0, out.length);
                return out;
            }
        }
        return data;
    }
    private static byte[] hex(String s) {
        int n = s.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
