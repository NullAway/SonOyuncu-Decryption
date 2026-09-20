package com.deluxe.sonoyuncu.utils;
public final class ConsoleColors {
    public static final String RESET  = "\u001B[0m";
    public static final String BOLD   = "\u001B[1m";
    public static final String DIM    = "\u001B[2m";
    private static final int[][] FROM = { { 0, 120, 255 }, { 170, 0, 255 }, { 255, 255, 255 } };
    private static final int[][] TO   = { { 0, 255, 180 },  { 255, 60, 130 }, { 90, 160, 255 } };
    private ConsoleColors() { }
    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }
    public static String gradient(String text, int idx) {
        int p = Math.floorMod(idx, FROM.length);
        int[] a = FROM[p], b = TO[p];
        StringBuilder sb = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\n') { sb.append(c); continue; }
            double t = len <= 1 ? 0 : (double) i / (len - 1);
            int r = (int) (a[0] + (b[0] - a[0]) * t);
            int g = (int) (a[1] + (b[1] - a[1]) * t);
            int bl = (int) (a[2] + (b[2] - a[2]) * t);
            sb.append("\u001B[38;2;").append(rgb(r, g, bl)).append('m').append(c);
        }
        sb.append(RESET);
        return sb.toString();
    }
    private static String ts() {
        return DIM + "[" + java.time.LocalTime.now().withNano(0) + "]" + RESET;
    }
    public static void step(String msg) {
        System.out.println(ts() + " " + BOLD + gradient(">> " + msg, 0));
    }
    private static String stepPrefix(String msg) {
        return ts() + " " + BOLD + gradient(">> " + msg, 0) + RESET;
    }
    public static void progressStart(String msg) {
        System.out.print(stepPrefix(msg));
        System.out.flush();
    }
    public static void progressUpdate(String msg, String counter) {
        System.out.print("\r" + stepPrefix(msg) + DIM + "  " + counter + RESET + "\u001B[K");
        System.out.flush();
    }
    public static void progressEnd() {
        System.out.println();
    }
    public static void info(String msg) {
        System.out.println(ts() + " " + gradient("  " + msg, 1));
    }
    public static void detail(String msg) {
        System.out.println(ts() + " " + DIM + "  " + msg + RESET);
    }
    public static void success(String msg) {
        System.out.println(ts() + " " + BOLD + gradient("[+] " + msg, 0));
    }
    public static void error(String msg) {
        System.out.println(ts() + " " + BOLD + "\u001B[38;2;255;60;60m[-]\u001B[0m " + msg + RESET);
    }
}
