package com.deluxe.sonoyuncu.utils;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
public final class ClassFileUtil {
    private ClassFileUtil() { }
    private static int u2(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }
    public static boolean isValidClassFile(byte[] b) {
        try {
            if (b == null || b.length < 24 || b[0] != (byte) 0xCA || b[1] != (byte) 0xFE
                    || b[2] != (byte) 0xBA || b[3] != (byte) 0xBE) {
                return false;
            }
            int major = u2(b, 6);
            if (major < 45 || major > 80) return false;
            int cp = u2(b, 8);
            int off = 10;
            int n = b.length;
            int idx = 1;
            while (idx < cp) {
                if (off >= n) return false;
                int tag = b[off] & 0xFF;
                off++;
                switch (tag) {
                    case 1: off += 2 + u2(b, off); break;
                    case 3: case 4: off += 4; break;
                    case 5: case 6: off += 8; idx++; break;
                    case 7: case 8: off += 2; break;
                    case 9: case 10: case 11: case 12: off += 4; break;
                    case 15: off += 3; break;
                    case 16: off += 2; break;
                    case 17: case 18: off += 4; break;
                    case 19: case 20: off += 2; break;
                    default: return false;
                }
                idx++;
            }
            return off + 6 <= n;
        } catch (Exception e) {
            return false;
        }
    }
    public static String getThisClass(byte[] b) {
        try {
            if (b[0] != (byte) 0xCA || b[1] != (byte) 0xFE
                    || b[2] != (byte) 0xBA || b[3] != (byte) 0xBE) return null;
            int cp = u2(b, 8);
            int off = 10;
            Map<Integer, String> utf8 = new HashMap<>();
            Map<Integer, Integer> cls = new HashMap<>();
            int idx = 1;
            while (idx < cp) {
                int tag = b[off] & 0xFF;
                off++;
                switch (tag) {
                    case 1: {
                        int ln = u2(b, off);
                        off += 2;
                        utf8.put(idx, new String(b, off, ln, StandardCharsets.UTF_8));
                        off += ln;
                        break;
                    }
                    case 3: case 4: off += 4; break;
                    case 5: case 6: off += 8; idx++; break;
                    case 7: cls.put(idx, u2(b, off)); off += 2; break;
                    case 8: off += 2; break;
                    case 9: case 10: case 11: case 12: off += 4; break;
                    case 15: off += 3; break;
                    case 16: off += 2; break;
                    case 17: case 18: off += 4; break;
                    case 19: case 20: off += 2; break;
                    default: return null;
                }
                idx++;
            }
            Integer nameIdx = cls.get(u2(b, off + 2));
            return nameIdx == null ? null : utf8.get(nameIdx);
        } catch (Exception e) {
            return null;
        }
    }
}
