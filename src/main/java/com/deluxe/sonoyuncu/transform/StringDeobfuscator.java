package com.deluxe.sonoyuncu.transform;
import com.deluxe.sonoyuncu.utils.ConsoleColors;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
public final class StringDeobfuscator {
    private StringDeobfuscator() { }
    public static void run(Path inTmp, Path outFinal) {
        boolean ok = false;
        try {
            ConsoleColors.step("Deobfuscating strings (auto-detect)");
            Path map = Files.createTempFile("sostrmap", ".txt");
            try {
                String javaBin = System.getProperty("java.home") + File.separator + "bin"
                        + File.separator + (File.separatorChar == '\\' ? "java.exe" : "java");
                String cp = System.getProperty("java.class.path");
                List<String> cmd = new ArrayList<>();
                cmd.add(javaBin);
                cmd.add("-cp"); cmd.add(cp);
                cmd.add("-Xmx1g");
                cmd.add("-Dso.worker=true");
                cmd.add(HarvestRunner.class.getName());
                cmd.add(inTmp.toAbsolutePath().toString());
                cmd.add(map.toAbsolutePath().toString());
                java.util.LinkedHashSet<String> jars = new java.util.LinkedHashSet<>();
                collectJars(new File(System.getProperty("user.dir")), jars, 0);
                String appdata = System.getenv("APPDATA");
                if (appdata != null) {
                    collectJars(new File(appdata, ".sonoyuncu"), jars, 0);
                }
                for (String j : jars) cmd.add(j);
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (InputStream in = p.getInputStream()) {
                    byte[] buf = new byte[4096];
                    while (in.read(buf) > 0) {  }
                }
                if (!p.waitFor(240, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    ConsoleColors.error("Harvest worker timed out, skipping string deobfuscation");
                } else if (p.exitValue() != 0) {
                    ConsoleColors.error("Harvest worker failed, skipping string deobfuscation");
                } else if (Files.size(map) == 0) {
                    ConsoleColors.detail("No harvestable string fields found");
                } else {
                    ConsoleColors.step("Inlining deobfuscated strings");
                    StringTransformer.Result r = StringTransformer.transform(inTmp, outFinal, map);
                    if (r.classesTouched == 0) {
                        ConsoleColors.detail("No inlined usage sites found, keeping decrypted jar");
                        Files.move(inTmp, outFinal, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        ConsoleColors.success("String deobfuscation: " + r.stringsInlined
                                + " strings deobfuscated (" + r.distinct.size() + " unique) across "
                                + r.classesTouched + " classes — " + r.getstaticReplaced
                                + " field reads + " + r.arrayLoadsReplaced
                                + " array reads + " + r.seedConstantsReplaced
                                + " seed constants inlined");
                        if (!r.samples.isEmpty()) {
                            ConsoleColors.detail("Samples:");
                            for (String s : r.samples) {
                                String preview = s.length() > 60 ? s.substring(0, 60) + "..." : s;
                                ConsoleColors.detail("  \"" + preview.replace("\n", "\\n") + "\"");
                            }
                        }
                    }
                    ok = true;
                }
            } finally {
                try { Files.deleteIfExists(map); } catch (Exception ignored) { }
                try { Files.deleteIfExists(inTmp); } catch (Exception ignored) { }
            }
        } catch (Throwable t) {
            ConsoleColors.error("String deobfuscation failed: " + t);
        }
        if (Files.exists(outFinal)) {
            try {
                Path cleaned = Files.createTempFile("soclean", ".jar");
                try {
                    DeadCodeRemover.Result cr = DeadCodeRemover.clean(outFinal, cleaned);
                    if (cr.fieldsRemoved > 0 || cr.junkInsnRemoved > 0) {
                        Files.move(cleaned, outFinal, StandardCopyOption.REPLACE_EXISTING);
                        ConsoleColors.success("Dead-code cleanup: " + cr.fieldsRemoved
                                + " dead string fields removed, " + cr.junkInsnRemoved
                                + " junk instructions stripped (" + cr.classesCleaned + " classes)");
                        if (!cr.samples.isEmpty()) {
                            for (String s : cr.samples) ConsoleColors.detail("  " + s);
                        }
                    } else {
                        ConsoleColors.detail("No dead string fields or junk code found");
                    }
                } finally {
                    try { Files.deleteIfExists(cleaned); } catch (Exception ignored) { }
                }
            } catch (Throwable t) {
                ConsoleColors.error("Dead-code cleanup skipped: " + t);
            }
        }
        if (!ok && Files.notExists(outFinal)) {
            try {
                Path candidate = inTmp;
                if (Files.exists(candidate)) {
                    Files.move(candidate, outFinal, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) { }
        }
    }
    private static void collectJars(File dir, java.util.Set<String> out, int depth) {
        if (dir == null || depth > 3 || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectJars(f, out, depth + 1);
            } else if (f.getName().endsWith(".jar")) {
                try {
                    String path = f.getAbsolutePath();
                    if (path.endsWith("So-Decrypt.jar")) continue;
                    out.add(path);
                } catch (Exception ignored) { }
            }
        }
    }
}
