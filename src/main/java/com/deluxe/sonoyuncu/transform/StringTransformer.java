package com.deluxe.sonoyuncu.transform;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
public final class StringTransformer {
    public static final class Result {
        public int getstaticReplaced;
        public int arrayLoadsReplaced;
        public int seedConstantsReplaced;
        public int stringsInlined;                                   
        public final java.util.Set<String> distinct = new java.util.HashSet<>();
        public int classesTouched;
        public final List<String> samples = new java.util.ArrayList<>();
        void countString(String v) {
            stringsInlined++;
            distinct.add(v);
        }
    }
    private static final class FieldValue {
        final boolean isArray;
        final String single;   
        final String[] array;  
        FieldValue(boolean isArray, String single, String[] array) {
            this.isArray = isArray; this.single = single; this.array = array;
        }
    }
    private static final class ScanResult {
        final Map<String, FieldValue> targets = new HashMap<>();  
        final Map<String, String> seedToValue = new HashMap<>();  
        boolean hasIndy;                                          
    }
    private StringTransformer() { }
    public static Result transform(Path inJar, Path outJar, Path mappingFile) throws IOException {
        Map<String, FieldValue> fields = loadMapping(mappingFile);
        Set<String> mutated = new HashSet<>();
        Result result = new Result();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(inJar)))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                entries.put(e.getName(), zin.readAllBytes());
            }
        }
        Map<String, ScanResult> scans = new HashMap<>();
        for (Map.Entry<String, byte[]> en : entries.entrySet()) {
            String name = en.getKey();
            if (!name.endsWith(".class")) continue;
            try {
                ScanResult sr = scanClass(en.getValue(), fields, mutated);
                if (!sr.targets.isEmpty()) scans.put(name, sr);
            } catch (Exception ignored) { }
        }
        List<String> errors = new java.util.ArrayList<>();
        boolean dbg = Boolean.getBoolean("so.dbg");
        try (ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outJar))) {
            for (Map.Entry<String, byte[]> en : entries.entrySet()) {
                byte[] data = en.getValue();
                ScanResult sr = scans.get(en.getKey());
                if (dbg && en.getKey().equals("cn.class") && sr != null) {
                    System.out.println("DBG cn targets=" + sr.targets.keySet() + " indy=" + sr.hasIndy);
                }
                if (dbg && en.getKey().equals("cn.class") && sr == null) {
                    System.out.println("DBG cn has NO scan");
                }
                if (sr != null) {
                    try {
                        byte[] rewritten = rewriteClass(data, sr, fields, mutated, result);
                        if (rewritten != null) {
                            result.classesTouched++;
                            data = rewritten;
                        }
                    } catch (Throwable t) {
                        if (dbg && en.getKey().equals("cn.class")) {
                            System.out.println("DBG cn first-attempt threw: " + t);
                        }
                        if (errors.size() < 5) {
                            java.io.StringWriter sw = new java.io.StringWriter();
                            t.printStackTrace(new java.io.PrintWriter(sw));
                            errors.add(en.getKey() + ": " + sw);
                        }
                    }
                }
                zout.putNextEntry(new ZipEntry(en.getKey()));
                zout.write(data);
                zout.closeEntry();
            }
        }
        for (String err : errors) System.out.println("TRANSFORM-ERR " + err);
        return result;
    }
    private static Map<String, FieldValue> loadMapping(Path f) throws IOException {
        Map<String, FieldValue> out = new HashMap<>();
        if (!Files.exists(f)) return out;
        for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            if (line.isEmpty()) continue;
            String[] parts = line.split(" ");
            try {
                String ownerDotField = dec(parts[0]);
                String owner = dec(parts[1]);
                String fname = ownerDotField.substring(owner.length() + 1);
                if (parts[2].equals("S")) {
                    String v = dec(parts[3]);
                    if (!v.isEmpty()) out.put(owner + '.' + fname, new FieldValue(false, v, null));
                } else {
                    int n = Integer.parseInt(parts[3]);
                    String[] arr = new String[n];
                    for (int i = 0; i < n; i++) arr[i] = dec(parts[4 + i]);
                    out.put(owner + '.' + fname, new FieldValue(true, null, arr));
                }
            } catch (Exception ignored) { }
        }
        return out;
    }
    private static String dec(String b64) {
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
    }
    private static ScanResult scanClass(byte[] data, Map<String, FieldValue> fields,
                                        Set<String> mutated) {
        ScanResult sr = new ScanResult();
        ClassReader cr = new ClassReader(data);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            String className;
            boolean inClinit;
            String pendingSeed;   
            @Override public void visit(int version, int access, String name, String sig,
                                        String superName, String[] interfaces) {
                className = name;
            }
            @Override public FieldVisitor visitField(int access, String name, String desc,
                                                     String sig, Object value) {
                FieldValue fv = fields.get(className + '.' + name);
                if (fv != null && (access & Opcodes.ACC_STATIC) != 0
                        && ((fv.isArray && desc.equals("[Ljava/lang/String;"))
                            || (!fv.isArray && desc.equals("Ljava/lang/String;")))) {
                    sr.targets.put(name, fv);
                }
                return null;
            }
            @Override public MethodVisitor visitMethod(int access, String name, String desc,
                                                       String sig, String[] exceptions) {
                inClinit = name.equals("<clinit>");
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitLdcInsn(Object cst) {
                        if (inClinit && cst instanceof String) pendingSeed = (String) cst;
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String name,
                                                         String d) {
                        if (opcode == Opcodes.PUTSTATIC) {
                            String key = owner + '.' + name;
                            if (!inClinit) {
                                mutated.add(key);
                            } else if (pendingSeed != null) {
                                FieldValue fv = sr.targets.get(name);
                                if (fv != null && !fv.isArray) {
                                    sr.seedToValue.putIfAbsent(pendingSeed, fv.single);
                                }
                            }
                        }
                        pendingSeed = null;
                    }
                    @Override public void visitInsn(int op) { pendingSeed = null; }
                    @Override public void visitIntInsn(int op, int o) { pendingSeed = null; }
                    @Override public void visitVarInsn(int op, int v) { pendingSeed = null; }
                    @Override public void visitTypeInsn(int op, String t) { pendingSeed = null; }
                    @Override public void visitMethodInsn(int op, String o, String n, String d, boolean i) { pendingSeed = null; }
                    @Override public void visitJumpInsn(int op, Label l) { pendingSeed = null; }
                    @Override public void visitInvokeDynamicInsn(String n, String d, org.objectweb.asm.Handle b, Object... a) {
                        sr.hasIndy = true;
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        return sr;
    }
    private static byte[] rewriteClass(byte[] data, ScanResult sr, Map<String, FieldValue> fields,
                                       Set<String> mutated, Result result) {
        boolean dbg = Boolean.getBoolean("so.dbg");
        try {
            if (!sr.hasIndy) {
                byte[] out = rewrite(data, sr, fields, mutated, result, true);
                if (out != null && !reparseOk(out)) return null;
                return out;
            }
            byte[] out = rewrite(data, sr, fields, mutated, result, false);
            return (out != null && reparseOk(out)) ? out : null;
        } catch (Throwable t) {
            if (dbg) {
                System.out.println("DBG rewrite-THROW " + t);
                t.printStackTrace(System.out);
            }
            return null;
        }
    }
    private static boolean reparseOk(byte[] out) {
        if (out == null || out.length < 10) return false;
        int major = ((out[6] & 0xFF) << 8) | (out[7] & 0xFF);
        if (major < 45 || major > 70) return false;
        try {
            new ClassReader(out);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
    private static final class SafeWriter extends ClassWriter {
        int fallbacks;
        SafeWriter(int flags) { super(flags); }
        @Override protected String getCommonSuperClass(String t1, String t2) {
            try {
                return super.getCommonSuperClass(t1, t2);
            } catch (Throwable t) {
                fallbacks++;
                return "java/lang/Object";
            }
        }
    }
    private static byte[] rewrite(byte[] data, ScanResult sr, Map<String, FieldValue> fields,
                                  Set<String> mutated, Result result, boolean stripFrames) {
        int[] counts = new int[3]; 
        SafeWriter cw = new SafeWriter(stripFrames
                ? ClassWriter.COMPUTE_MAXS : ClassWriter.COMPUTE_FRAMES);
        ClassReader cr = new ClassReader(data);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override public void visit(int version, int access, String name, String sig,
                                        String superName, String[] interfaces) {
                super.visit(stripFrames ? Opcodes.V1_5 : version, access, name, sig,
                        superName, interfaces);
            }
            @Override public FieldVisitor visitField(int access, String name, String desc,
                                                     String sig, Object value) {
                FieldValue fv = sr.targets.get(name);
                if (fv != null && (access & Opcodes.ACC_STATIC) != 0) {
                    access &= ~Opcodes.ACC_FINAL;
                }
                return super.visitField(access, name, desc, sig, value);
            }
            @Override public MethodVisitor visitMethod(int access, String name, String desc,
                                                       String sig, String[] exceptions) {
                MethodVisitor mv = cw.visitMethod(access, name, desc, sig, exceptions);
                return new UsageReplacer(mv, sr, fields, mutated, result, counts);
            }
        }, ClassReader.SKIP_FRAMES);
        if (cw.fallbacks > 0) return null; 
        boolean touched = counts[0] + counts[1] + counts[2] > 0;
        if (!touched) return null;
        byte[] out = cw.toByteArray();
        int outMajor = ((out[6] & 0xFF) << 8) | (out[7] & 0xFF);
        if (outMajor < 45) {
            int target = stripFrames ? 49 : Math.max(((data[6] & 0xFF) << 8) | (data[7] & 0xFF), 45);
            out[4] = 0; out[5] = 0;
            out[6] = (byte) (target >> 8); out[7] = (byte) target;
        }
        return out;
    }
    private static final class UsageReplacer extends MethodVisitor {
        private final ScanResult sr;
        private final Map<String, FieldValue> fields;
        private final Set<String> mutated;
        private final Result result;
        private final int[] counts;
        private FieldValue heldArr;
        private String heldArrOwner, heldArrName, heldArrDesc;
        private Integer heldSlot;
        private Integer heldIdx;
        private final Map<Integer, FieldValue> arrLocals = new HashMap<>();
        UsageReplacer(MethodVisitor mv, ScanResult sr, Map<String, FieldValue> fields,
                      Set<String> mutated, Result result, int[] counts) {
            super(Opcodes.ASM9, mv);
            this.sr = sr;
            this.fields = fields;
            this.mutated = mutated;
            this.result = result;
            this.counts = counts;
        }
        private void flushAll() {
            if (heldArr != null) {
                super.visitFieldInsn(Opcodes.GETSTATIC, heldArrOwner, heldArrName, heldArrDesc);
                heldArr = null;
            }
            if (heldSlot != null) {
                super.visitVarInsn(Opcodes.ALOAD, heldSlot);
                heldSlot = null;
            }
            if (heldIdx != null) {
                super.visitLdcInsn(heldIdx);
                heldIdx = null;
            }
        }
        private void noteSample(String s) {
            if (result.samples.size() < 8 && s.length() >= 4 && !result.samples.contains(s)) {
                result.samples.add(s);
            }
        }
        private boolean isTargetArray(String owner, String name, String desc) {
            FieldValue fv = fields.get(owner + '.' + name);
            return fv != null && fv.isArray && desc.equals("[Ljava/lang/String;")
                    && !mutated.contains(owner + '.' + name);
        }
        @Override public void visitLdcInsn(Object cst) {
            if (cst instanceof Integer) {
                if (heldIdx != null) super.visitLdcInsn(heldIdx); 
                heldIdx = (Integer) cst;   
                return;
            }
            flushAll();
            if (cst instanceof String) {
                String v = sr.seedToValue.get(cst);
                if (v != null) {
                    counts[2]++;
                    result.seedConstantsReplaced++;
                    result.countString(v);
                    noteSample(v);
                    super.visitLdcInsn(v);
                    return;
                }
            }
            super.visitLdcInsn(cst);
        }
        @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            FieldValue fv = fields.get(owner + '.' + name);
            if (opcode == Opcodes.GETSTATIC && fv != null && !fv.isArray
                    && desc.equals("Ljava/lang/String;")
                    && !mutated.contains(owner + '.' + name)) {
                flushAll();
                counts[0]++;
                result.getstaticReplaced++;
                result.countString(fv.single);
                noteSample(fv.single);
                super.visitLdcInsn(fv.single);
                return;
            }
            if (opcode == Opcodes.GETSTATIC && isTargetArray(owner, name, desc)) {
                if (heldSlot != null) {
                    super.visitVarInsn(Opcodes.ALOAD, heldSlot);
                    heldSlot = null;
                }
                if (heldArr != null) {
                    super.visitFieldInsn(Opcodes.GETSTATIC, heldArrOwner, heldArrName, heldArrDesc);
                }
                heldArr = fv; heldArrOwner = owner; heldArrName = name; heldArrDesc = desc;
                return; 
            }
            flushAll();
            super.visitFieldInsn(opcode, owner, name, desc);
        }
        @Override public void visitVarInsn(int opcode, int var) {
            if (opcode == Opcodes.ASTORE && heldArr != null) {
                if (heldIdx == null && heldSlot == null) {
                    arrLocals.put(var, heldArr);
                    heldArr = null;
                    return;
                }
                flushAll();
                super.visitVarInsn(opcode, var);
                return;
            }
            if (opcode == Opcodes.ALOAD && arrLocals.containsKey(var)) {
                if (heldArr != null) {
                    super.visitFieldInsn(Opcodes.GETSTATIC, heldArrOwner, heldArrName, heldArrDesc);
                    heldArr = null;
                }
                if (heldSlot != null) {
                    super.visitVarInsn(Opcodes.ALOAD, heldSlot);
                }
                heldSlot = var;
                return;
            }
            flushAll();
            super.visitVarInsn(opcode, var);
        }
        @Override public void visitInsn(int opcode) {
            if (opcode == Opcodes.AALOAD) {
                FieldValue fv = heldArr != null ? heldArr
                        : (heldSlot != null ? arrLocals.get(heldSlot) : null);
                if (heldIdx != null && fv != null) {
                    int idx = heldIdx;
                    if (idx >= 0 && idx < fv.array.length && fv.array[idx] != null) {
                        heldArr = null; heldSlot = null; heldIdx = null;
                        counts[1]++;
                        result.arrayLoadsReplaced++;
                        result.countString(fv.array[idx]);
                        noteSample(fv.array[idx]);
                        super.visitLdcInsn(fv.array[idx]);
                        return;
                    }
                }
                flushAll();
                super.visitInsn(opcode);
                return;
            }
            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                if (heldIdx != null) super.visitLdcInsn(heldIdx); 
                heldIdx = (opcode == Opcodes.ICONST_M1) ? -1 : opcode - Opcodes.ICONST_0;
                return;
            }
            flushAll();
            super.visitInsn(opcode);
        }
        @Override public void visitIntInsn(int opcode, int operand) {
            if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                if (heldIdx != null) super.visitLdcInsn(heldIdx);
                heldIdx = operand;
                return;
            }
            flushAll();
            super.visitIntInsn(opcode, operand);
        }
        private void reset() { flushAll(); }
        private void discard() {
            heldArr = null; heldSlot = null; heldIdx = null;
        }
        @Override public void visitTypeInsn(int opcode, String type) { reset(); super.visitTypeInsn(opcode, type); }
        @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            reset(); super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
        @Override public void visitJumpInsn(int opcode, Label label) { reset(); super.visitJumpInsn(opcode, label); }
        @Override public void visitLabel(Label label) { reset(); super.visitLabel(label); }
        @Override public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
            reset(); super.visitFrame(type, nLocal, local, nStack, stack);
        }
        @Override public void visitIincInsn(int var, int increment) { reset(); super.visitIincInsn(var, increment); }
        @Override public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            reset(); super.visitTableSwitchInsn(min, max, dflt, labels);
        }
        @Override public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            reset(); super.visitLookupSwitchInsn(dflt, keys, labels);
        }
        @Override public void visitMultiANewArrayInsn(String desc, int numDimensions) {
            reset(); super.visitMultiANewArrayInsn(desc, numDimensions);
        }
        @Override public void visitInvokeDynamicInsn(String name, String desc, org.objectweb.asm.Handle bsm, Object... bsmArgs) {
            reset(); super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
        }
        @Override public void visitMaxs(int maxStack, int maxLocals) {
            discard();   
            super.visitMaxs(maxStack, maxLocals);
        }
    }
}
