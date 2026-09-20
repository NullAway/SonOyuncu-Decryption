package com.deluxe.sonoyuncu;
import com.deluxe.sonoyuncu.decrypt.Decryptor;
import com.deluxe.sonoyuncu.utils.Banner;
import com.deluxe.sonoyuncu.utils.ConsoleColors;
import java.io.File;
import java.net.URISyntaxException;
public final class Main {
    private static final String OUTPUT_NAME = "game-decrypted-deluxe1447.jar";
    public static void main(String[] args) {
        Banner.print();
        String appdata = System.getenv("APPDATA");
        File src = new File(args.length > 0 ? args[0]
                : (appdata != null ? appdata : ".") + File.separator + ".sonoyuncu" + File.separator + "game.jar");
        File dst = args.length > 1 ? new File(args[1], OUTPUT_NAME)
                : new File(jarDir(), OUTPUT_NAME);
        ConsoleColors.success("Grabbing \"game.jar\" from .sonoyuncu");
        ConsoleColors.info("Input : " + src.getAbsolutePath());
        ConsoleColors.info("Output: " + dst.getAbsolutePath());
        System.out.println();
        if (!src.isFile()) {
            ConsoleColors.error("game.jar not found at " + src.getAbsolutePath());
            System.exit(1);
        }
        Decryptor.decryptJar(src, dst);
        System.exit(0);
    }
    private static File jarDir() {
        try {
            File jar = new File(Main.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File dir = jar.getParentFile();
            if (dir != null && dir.isDirectory()) return dir;
        } catch (URISyntaxException ignored) { }
        return new File(System.getProperty("user.dir"));
    }
}
