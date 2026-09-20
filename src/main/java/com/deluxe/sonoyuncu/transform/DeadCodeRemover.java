package com.deluxe.sonoyuncu.transform;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
public final class DeadCodeRemover {
    public static final class Result {
        public int fieldsRemoved;
        public int classesCleaned;
        public int junkInsnRemoved;
        public int clinitRemoved;
        public final List<String> samples = new ArrayList<>();
    }
    private record Cls(String name, ClassNode node) { }
    private DeadCodeRemover() { }
    public static Result clean(Path inJar, Path outJar) throws IOException {
        Result result = new Result();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(inJar)))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                entries.put(e.getName(), zin.readAllBytes());
            }
        }
        Map<String, Cls> classes = new LinkedHashMap<>();
        Set<String> globallyRead = new HashSet<>();          
        Set<String> writtenOutsideClinit = new HashSet<>();  
        for (Map.Entry<String, byte[]> en : entries.entrySet()) {
            if (!en.getKey().endsWith(".class")) continue;
            try {
                ClassNode cn = new ClassNode();
                new ClassReader(en.getValue()).accept(cn, ClassReader.SKIP_FRAMES);
                classes.put(en.getKey(), new Cls(cn.name, cn));
                for (MethodNode mn : cn.methods) {
                    boolean isClinit = mn.name.equals("<clinit>");
                    for (AbstractInsnNode ins : mn.instructions) {
                        if (!(ins instanceof FieldInsnNode)) continue;
                        FieldInsnNode f = (FieldInsnNode) ins;
                        String key = f.owner + '#' + f.name + ':' + f.desc;
                        if (f.getOpcode() == Opcodes.GETSTATIC) globallyRead.add(key);
                        else if (f.getOpcode() == Opcodes.PUTSTATIC && !isClinit) writtenOutsideClinit.add(key);
                    }
                }
            } catch (Throwable ignored) { }
        }
        try (ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outJar))) {
            for (Map.Entry<String, byte[]> en : entries.entrySet()) {
                byte[] data = en.getValue();
                Cls cls = classes.get(en.getKey());
                if (cls != null) {
                    try {
                        byte[] cleaned = cleanClass(cls.node, globallyRead, writtenOutsideClinit, result);
                        if (cleaned != null) data = cleaned;
                    } catch (Throwable ignored) { }
                }
                zout.putNextEntry(new ZipEntry(en.getKey()));
                zout.write(data);
                zout.closeEntry();
            }
        }
        return result;
    }
    private static byte[] cleanClass(ClassNode cn, Set<String> globallyRead,
                                     Set<String> writtenOutsideClinit, Result result) {
        MethodNode clinit = null;
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("<clinit>")) { clinit = mn; break; }
        }
        Set<String> deadFields = new HashSet<>();
        if (clinit != null) {
            for (FieldNode fn : cn.fields) {
                if ((fn.access & Opcodes.ACC_STATIC) == 0) continue;
                if (!"Ljava/lang/String;".equals(fn.desc) && !"[Ljava/lang/String;".equals(fn.desc)) continue;
                String key = cn.name + '#' + fn.name + ':' + fn.desc;
                if (!globallyRead.contains(key) && !writtenOutsideClinit.contains(key)) {
                    deadFields.add(key);
                }
            }
        }
        boolean changed = false;
        int removedFields = 0;
        if (!deadFields.isEmpty() && clinit != null) {
            boolean writesLive = false;
            for (AbstractInsnNode ins : clinit.instructions) {
                if (ins instanceof FieldInsnNode) {
                    FieldInsnNode f = (FieldInsnNode) ins;
                    if (f.getOpcode() == Opcodes.PUTSTATIC
                            && !deadFields.contains(f.owner + '#' + f.name + ':' + f.desc)) {
                        writesLive = true;
                        break;
                    }
                }
            }
            if (!writesLive) {
                cn.methods.remove(clinit);
                for (FieldNode fn : new ArrayList<>(cn.fields)) {
                    if (deadFields.contains(cn.name + '#' + fn.name + ':' + fn.desc)) {
                        cn.fields.remove(fn);
                        removedFields++;
                    }
                }
                changed = removedFields > 0;
                if (changed) result.clinitRemoved++;
            } else {
                Set<String> simple = new HashSet<>();
                for (AbstractInsnNode ins : clinit.instructions.toArray()) {
                    if (ins instanceof FieldInsnNode) {
                        FieldInsnNode f = (FieldInsnNode) ins;
                        if (f.getOpcode() == Opcodes.PUTSTATIC
                                && deadFields.contains(f.owner + '#' + f.name + ':' + f.desc)
                                && ins.getPrevious() instanceof LdcInsnNode
                                && ((LdcInsnNode) ins.getPrevious()).cst instanceof String) {
                            simple.add(f.owner + '#' + f.name + ':' + f.desc);
                            clinit.instructions.remove(ins);
                            clinit.instructions.remove(ins.getPrevious());
                        }
                    }
                }
                for (FieldNode fn : new ArrayList<>(cn.fields)) {
                    String key = cn.name + '#' + fn.name + ':' + fn.desc;
                    if (simple.contains(key)) {
                        cn.fields.remove(fn);
                        removedFields++;
                        changed = true;
                    }
                }
            }
            if (removedFields > 0) {
                result.fieldsRemoved += removedFields;
                if (result.samples.size() < 8) {
                    result.samples.add(cn.name + " -" + removedFields + " dead fields"
                            + (result.clinitRemoved > 0 ? " (clinit removed)" : ""));
                }
            }
        }
        int junk = 0;
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode ins : mn.instructions.toArray()) {
                if (ins.getOpcode() == Opcodes.NOP) {
                    mn.instructions.remove(ins);
                    junk++;
                }
            }
        }
        if (junk > 0) { result.junkInsnRemoved += junk; changed = true; }
        if (!changed) return null;
        boolean indy = false;
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode ins : mn.instructions) {
                if (ins.getOpcode() == Opcodes.INVOKEDYNAMIC) { indy = true; break; }
            }
            if (indy) break;
        }
        byte[] out;
        if (indy) {
            final int[] fallbacks = { 0 };
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                @Override protected String getCommonSuperClass(String t1, String t2) {
                    try {
                        return super.getCommonSuperClass(t1, t2);
                    } catch (Throwable t) {
                        fallbacks[0]++;
                        return "java/lang/Object";
                    }
                }
            };
            cn.accept(cw);
            if (fallbacks[0] > 0) return null;
            out = cw.toByteArray();
        } else {
            cn.version = Opcodes.V1_5;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            out = cw.toByteArray();
        }
        int outMajor = ((out[6] & 0xFF) << 8) | (out[7] & 0xFF);
        if (outMajor < 45) {
            int target = indy ? 52 : 49;
            out[4] = 0; out[5] = 0;
            out[6] = (byte) (target >> 8); out[7] = (byte) target;
        }
        new ClassReader(out); 
        result.classesCleaned++;
        return out;
    }
}
