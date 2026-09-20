package com.deluxe.sonoyuncu.decrypt;
public final class ClassDecryptor {
    private static final double XMM6 = 2.3283064365386963e-10;
    private static final double XMM7 = 0.5;
    private ClassDecryptor() { }
    public static int mixerSeed(int[] T, int i) {
        int r11 = i;
        int idx0 = (r11 + 0x7f) & 0xff;
        int eax = r11 + r11 * 2;
        int r8 = eax * 8 - 0x18;
        int edx = r8 & 0xffff;
        r8 = r8 & 0xffff0000;
        int ecx = T[idx0];
        eax = ecx & 0xffff;
        int rsp64 = eax;
        eax = eax + edx;
        int r9 = eax;
        int rsp70 = ecx;
        eax = eax & 0xffff;
        ecx = ecx & 0xffff0000;
        int rsp60 = ecx;
        r9 = r9 & 0xffff0000;
        r9 = r9 + ecx;
        r9 = r9 + r8;
        r9 = r9 | eax;
        eax = r11 + r11 * 2;
        ecx = eax * 4 - 0xc;
        eax = ecx & 0xffff;
        ecx = ecx & 0xffff0000;
        edx = edx + eax;
        int r10 = edx;
        r10 = r10 & 0xffff0000;
        eax = r8 + ecx;
        r10 = r10 + eax;
        eax = edx & 0xffff;
        r10 = r10 | eax;
        int sel = (r11 - 1) & 3;
        int rot;
        if ((sel & 1) == 0) {
            int cl = sel * 4 + 4;
            rot = r9 << (cl & 31);
        } else {
            int a = sel + 2;
            int cl = ((a + a * 2) * 2) >> 1;
            rot = r9 >> (cl & 31);
        }
        r9 = r9 ^ rot;
        edx = r10 & 0xffff;
        r10 = r10 & 0xffff0000;
        eax = r9 & 0xffff;
        edx = edx + eax;
        r9 = r9 & 0xffff0000;
        eax = rsp70;
        r10 = r10 + r9;
        ecx = edx & 0xffff;
        edx = edx & 0xffff0000;
        eax = eax >> 2;
        r10 = r10 + edx;
        edx = rsp64;
        r10 = r10 | ecx;
        int r8b = eax & 0xff;
        r10 = r10 & 0xffff03ff;
        r8b = r8b + ecx;
        ecx = r8b;
        int r8q = (ecx & 0xffffffff) >>> 0xa;
        ecx = ecx & 0xffff0000;
        r8b = r8q & 0x3f;
        ecx = ecx + r10;
        int rcx = (ecx >>> 0xa) | r8b;
        int idx1 = rcx & 0xff;
        ecx = T[idx1];
        eax = ecx & 0xffff;
        ecx = ecx & 0xffff0000;
        edx = edx + eax;
        eax = rsp60;
        eax = eax + ecx;
        r8 = edx;
        r8 = r8 & 0xffff0000;
        r8 = r8 + eax;
        eax = edx & 0xffff;
        r8 = r8 | eax;
        return r8;
    }
    public static byte[] decrypt(byte[] inner, int[] T, int lo, int hi) {
        byte[] out = new byte[inner.length];
        int base = lo <= hi ? lo : hi;
        int rng = Math.abs(lo - hi) + 1;
        for (int i = 0; i < inner.length; i++) {
            int p = mixerSeed(T, i + 1);
            double u = (double) p * XMM6 + XMM7;
            int floorVal = (int) Math.floor(rng * u);
            int mapped = (floorVal + base) & 0xff;
            out[i] = (byte) (inner[i] ^ mapped);
        }
        return out;
    }
}
