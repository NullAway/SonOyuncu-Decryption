package com.deluxe.sonoyuncu.decrypt;
import com.deluxe.sonoyuncu.transform.StringDeobfuscator;
import com.deluxe.sonoyuncu.utils.ClassFileUtil;
import com.deluxe.sonoyuncu.utils.ConsoleColors;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
public final class Decryptor {
    private static final int LO = 23;
    private static final int HI = 118;
    private Decryptor() { }
    public static void decryptJar(File src, File dst) {
        long t0 = System.currentTimeMillis();
        int ok = 0, ncls = 0, other = 0;
        List<String> failed = new ArrayList<>();
        File part = new File(dst.getParentFile(), "." + dst.getName() + ".part");
        try (ZipFile zip = new ZipFile(src);
             ZipOutputStream zo = new ZipOutputStream(Files.newOutputStream(part.toPath()))) {
            ConsoleColors.step("Decrypting layer 1 (AES-256-CBC)");
            ConsoleColors.step("Decrypting layer 2 (stream mixer)");
            ConsoleColors.progressStart("Validating class files");
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] raw;
                try (InputStream in = zip.getInputStream(entry)) {
                    raw = in.readAllBytes();
                }
                byte[] out = raw;
                if (entry.getName().endsWith(".class")) {
                    ncls++;
                    try {
                        byte[] aesOut = AesDecryptor.decrypt(raw);
                        byte[] inner = AesDecryptor.unpad(aesOut);
                        int[] T = TTableBuilder.build(inner.length);
                        byte[] pl = ClassDecryptor.decrypt(inner, T, LO, HI);
                        if (ClassFileUtil.isValidClassFile(pl)
                                && ClassFileUtil.getThisClass(pl) != null
                                && ClassFileUtil.getThisClass(pl).equals(
                                        entry.getName().substring(0, entry.getName().length() - 6))) {
                            out = pl;
                            ok++;
                        } else {
                            failed.add(entry.getName());
                        }
                    } catch (Exception e) {
                        failed.add(entry.getName() + " (" + e.getClass().getSimpleName() + ")");
                    }
                } else {
                    other++;
                }
                zo.putNextEntry(new ZipEntry(entry.getName()));
                zo.write(out);
                zo.closeEntry();
                if (ncls > 0 && ncls % 200 == 0) {
                    ConsoleColors.progressUpdate("Validating class files",
                            ok + "/" + ncls + " classes, "
                                    + String.format("%.1fs", (System.currentTimeMillis() - t0) / 1000.0));
                }
            }
        } catch (IOException e) {
            ConsoleColors.error("IO error: " + e.getMessage());
            return;
        }
        double dt = (System.currentTimeMillis() - t0) / 1000.0;
        ConsoleColors.progressUpdate("Validating class files",
                ok + "/" + ncls + " classes, " + String.format("%.1fs", dt));
        ConsoleColors.progressEnd();
        ConsoleColors.success("DONE: " + ok + "/" + ncls + " classes decrypted+validated in "
                + String.format("%.1fs", dt) + " (other files: " + other + ")");
        if (!failed.isEmpty()) {
            ConsoleColors.error("Failed: " + failed.size());
            int lim = Math.min(20, failed.size());
            for (int i = 0; i < lim; i++) ConsoleColors.detail("  FAIL " + failed.get(i));
        }
        StringDeobfuscator.run(part.toPath(), dst.toPath());
        ConsoleColors.success("OUTPUT: " + dst.getAbsolutePath());
    }
}
