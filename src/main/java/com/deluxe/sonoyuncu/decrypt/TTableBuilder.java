package com.deluxe.sonoyuncu.decrypt;
public final class TTableBuilder {
    public static final int[] SEED8 = {
            0x1b427c9a, 0xf1f31f86, 0xa075e066, 0xc1304266,
            0x90075276, 0x9fdc9fdc, 0x7a58b1bd, 0xca69404d
    };
    private TTableBuilder() { }
    private static int sar32(int x, int c) {
        return x >> (c & 31);
    }
    private static int smod3(int x) {
        int q = x / 3;
        return x - 3 * q;
    }
    public static int[] build(int innerLen) {
        int d = innerLen * 36;
        int[] data = { d, d, d };
        int r9 = SEED8[0], r11 = SEED8[1], rbx = SEED8[2], edi = SEED8[3];
        int esi = SEED8[4], r14 = SEED8[5], r15 = SEED8[6], r12 = SEED8[7];
        int[] out = new int[256];
        int blk = 0;
        for (int r10 = 0; r10 < 0x100; r10 += 0x20) {
            int[] ks = { 0, 8, 0x10, 0x18 };
            for (int ki = 0; ki < 4; ki++) {
                int k = ks[ki];
                int edx = data[smod3(r10 + k) % 3];
                int o = blk * 8;
                if (ki == 0 || ki == 2) {
                    rbx = rbx + sar32(edx, 8);
                    int ecx = r11 + edx * 2;
                    r11 = edx << 5;
                    int eax = ecx << 0xb;
                    r11 = r11 + r9; r11 ^= eax;
                    r9 = rbx + ecx;
                    eax = (edx << 4) + r11; edi = edi + eax;
                    eax = rbx >>> 2; rbx = rbx + edi; r9 ^= eax;
                    eax = (edx << 0x10) + r9; esi = esi + eax;
                    eax = edi << 8; edi = edi + esi; rbx ^= eax;
                    eax = sar32(edx, 6) + rbx; r14 = r14 + eax;
                    eax = esi >>> 0x10; esi = esi + r14; edi ^= eax;
                    eax = sar32(edx, 2) + edi; edx = edx << 0xe;
                    r15 = r15 + eax;
                    eax = r14 << 0xa; r14 = r14 + r15; esi ^= eax;
                    eax = r15 >>> 4; edx = edx + esi; r14 ^= eax;
                    r12 = r12 + edx; r15 = r15 + r12; r11 = r11 + r14;
                    eax = r12 << 8; r12 = r12 + r11; r15 ^= eax;
                    eax = r11 >>> 9; r9 = r9 + r15; r12 ^= eax;
                    rbx = rbx + r12; r11 = r11 + r9;
                    out[o] = r11; out[o + 1] = r9; out[o + 2] = rbx; out[o + 3] = edi;
                    out[o + 4] = esi; out[o + 5] = r14; out[o + 6] = r15; out[o + 7] = r12;
                } else {
                    rbx = rbx + sar32(edx, 8);
                    int ecx = r9 + edx * 2;
                    r9 = edx << 5; int eax = ecx << 0xb;
                    r9 = r9 + r11; r9 ^= eax;
                    r11 = rbx + ecx;
                    eax = (edx << 4) + r9; edi = edi + eax;
                    eax = rbx >>> 2; rbx = rbx + edi; r11 ^= eax;
                    eax = (edx << 0x10) + r11; esi = esi + eax;
                    eax = edi << 8; edi = edi + esi; rbx ^= eax;
                    eax = sar32(edx, 6) + rbx; r14 = r14 + eax;
                    eax = esi >>> 0x10; esi = esi + r14; edi ^= eax;
                    eax = sar32(edx, 2) + edi; edx = edx << 0xe;
                    r15 = r15 + eax;
                    eax = r14 << 0xa; r14 = r14 + r15; esi ^= eax;
                    eax = r15 >>> 4; edx = edx + esi; r14 ^= eax;
                    r12 = r12 + edx; r15 = r15 + r12; r9 = r9 + r14;
                    eax = r12 << 8; r12 = r12 + r9; r15 ^= eax;
                    eax = r9 >>> 9; r11 = r11 + r15; r12 ^= eax;
                    r9 = r9 + r11; rbx = rbx + r12;
                    out[o] = r9; out[o + 1] = r11; out[o + 2] = rbx; out[o + 3] = edi;
                    out[o + 4] = esi; out[o + 5] = r14; out[o + 6] = r15; out[o + 7] = r12;
                }
                blk++;
            }
        }
        return out;
    }
}
