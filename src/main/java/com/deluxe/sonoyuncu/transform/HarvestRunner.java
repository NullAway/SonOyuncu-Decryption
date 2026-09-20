package com.deluxe.sonoyuncu.transform;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
public final class HarvestRunner {
    private HarvestRunner() { }
    public static void main(String[] args) {
        try {
            File jar = new File(args[0]);
            File out = new File(args[1]);
            List<URL> cp = new ArrayList<>();
            cp.add(jar.toURI().toURL());
            for (int i = 2; i < args.length; i++) {
                try {
                    cp.add(new File(args[i]).toURI().toURL());
                } catch (Exception ignored) { }
            }
            URLClassLoader loader = new URLClassLoader(
                    cp.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
            run(loader, jar, out);
        } catch (Throwable t) {
            System.err.println("harvest failed: " + t);
            t.printStackTrace();
            if (Boolean.getBoolean("so.worker")) System.exit(2);
        }
        if (Boolean.getBoolean("so.worker")) System.exit(0);
    }
    private static void run(URLClassLoader loader, File jar, File out) throws Exception {
        List<String> classNames = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.endsWith(".class") && !n.endsWith("module-info.class")
                        && !n.endsWith("package-info.class")) {
                    classNames.add(n.substring(0, n.length() - 6).replace('/', '.'));
                }
            }
        }
        List<Class<?>> loaded = new ArrayList<>();
        List<String> loadFailed = new ArrayList<>();
        for (String name : classNames) {
            try {
                loaded.add(Class.forName(name, false, loader));
            } catch (Throwable t) {
                loadFailed.add(name);
            }
        }
        int fullyInited = 0;
        List<String> initFailed = new ArrayList<>();
        for (Class<?> c : loaded) {
            try {
                Class.forName(c.getName(), true, loader);
                fullyInited++;
            } catch (Throwable t) {
                initFailed.add(c.getName());
            }
        }
        Map<String, String[]> harvested = new LinkedHashMap<>();
        Map<String, Boolean> isArr = new HashMap<>();
        for (Class<?> c : loaded) {
            dumpFields(c, harvested, isArr);
        }
        int patched = 0, patchedMore = 0;
        List<Class<?>> patchedClasses = new ArrayList<>();
        for (String name : initFailed) {
            Class<?> c = tryHarnessInit(loader, name);
            if (c != null) { patched++; patchedClasses.add(c); }
        }
        for (String name : loadFailed) {
            Class<?> c = tryHarnessInit(loader, name);
            if (c != null) { patched++; patchedClasses.add(c); }
        }
        for (Class<?> c : patchedClasses) {
            int before = harvested.size();
            dumpFields(c, harvested, isArr);
            if (harvested.size() > before) patchedMore++;
        }
        int strings = 0, arrays = 0;
        try (BufferedWriter w = new BufferedWriter(new FileWriter(out, StandardCharsets.UTF_8))) {
            for (Map.Entry<String, String[]> en : harvested.entrySet()) {
                String ownerDotField = en.getKey();
                int dot = ownerDotField.lastIndexOf('.');
                String owner = ownerDotField.substring(0, dot);
                String[] v = en.getValue();
                if (isArr.get(ownerDotField)) {
                    arrays++;
                    StringBuilder sb = new StringBuilder();
                    sb.append(b64(ownerDotField)).append(' ').append(b64(owner))
                      .append(" A ").append(v.length);
                    for (String e : v) sb.append(' ').append(b64(e == null ? "" : e));
                    w.write(sb.toString());
                    w.write('\n');
                } else {
                    strings++;
                    w.write(b64(ownerDotField));
                    w.write(' ');
                    w.write(b64(owner));
                    w.write(" S ");
                    w.write(b64(v[0]));
                    w.write('\n');
                }
            }
        }
        System.out.println("harvested " + strings + " string fields, " + arrays + " string[] fields"
                + " from " + loaded.size() + "/" + classNames.size() + " classes ("
                + fullyInited + " fully initialized, " + patched + " harness retries, "
                + patchedMore + " gave new values)");
    }
    private static void dumpFields(Class<?> c, Map<String, String[]> harvested, Map<String, Boolean> isArr) {
        try {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (!java.lang.reflect.Modifier.isStatic(mods)) continue;
                Class<?> t = f.getType();
                if (t != String.class && t != String[].class) continue;
                String key = c.getName().replace('.', '/') + '.' + f.getName();
                if (harvested.containsKey(key)) continue; 
                f.setAccessible(true);
                Object v;
                try {
                    v = f.get(null);
                } catch (Throwable ignored) {
                    continue;
                }
                if (t == String.class) {
                    if (v == null) continue;
                    harvested.put(key, new String[] { (String) v });
                    isArr.put(key, Boolean.FALSE);
                } else {
                    String[] arr = (String[]) v;
                    if (arr == null || arr.length == 0) continue;
                    harvested.put(key, arr.clone());
                    isArr.put(key, Boolean.TRUE);
                }
            }
        } catch (Throwable ignored) { }
    }
    private static Class<?> tryHarnessInit(URLClassLoader cp, String name) {
        try {
            byte[] orig = readFromLoader(cp, name.replace('.', '/'));
            if (orig == null) return null;
            byte[] patched = makeHarness(orig);
            HarnessLoader pl = new HarnessLoader(cp, name, patched);
            Class<?> c = pl.loadAndInit();
            if (c == null) return null;
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod(HARNESS_METHOD);
                m.setAccessible(true);
                m.invoke(null);  
            } catch (Throwable ignored) { }
            return c;
        } catch (Throwable t) {
            return null;
        }
    }
    private static final String HARNESS_METHOD = "soInit";
    private static byte[] makeHarness(byte[] orig) {
        final String[] clinitName = { HARNESS_METHOD };
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(orig).accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override public void visit(int version, int access, String name, String sig,
                                        String superName, String[] interfaces) {
                super.visit(Opcodes.V1_5, access & ~(Opcodes.ACC_FINAL | Opcodes.ACC_ENUM),
                        name, sig, "java/lang/Object", interfaces);
            }
            @Override public MethodVisitor visitMethod(int access, String name, String desc,
                                                       String sig, String[] exceptions) {
                boolean isClinit = name.equals("<clinit>");
                boolean isCtor = name.equals("<init>");
                if ((access & Opcodes.ACC_ABSTRACT) != 0) {
                    return cw.visitMethod(access, name, desc, sig, exceptions); 
                }
                if (isClinit) {
                    String hn = clinitName[0];
                    clinitName[0] = hn + "$";
                    return cw.visitMethod(access, hn, desc, sig, exceptions);
                }
                MethodVisitor mv = cw.visitMethod(access & ~(Opcodes.ACC_NATIVE),
                        name, desc, sig, exceptions);
                emitStub(mv, desc, isCtor);
                return null;
            }
        }, ClassReader.SKIP_FRAMES);
        return cw.toByteArray();
    }
    private static void emitStub(MethodVisitor mv, String desc, boolean isCtor) {
        if (isCtor) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        }
        Type ret = Type.getReturnType(desc);
        switch (ret.getSort()) {
            case Type.VOID: break;
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT: mv.visitInsn(Opcodes.ICONST_0); break;
            case Type.LONG: mv.visitInsn(Opcodes.LCONST_0); break;
            case Type.FLOAT: mv.visitInsn(Opcodes.FCONST_0); break;
            case Type.DOUBLE: mv.visitInsn(Opcodes.DCONST_0); break;
            default: mv.visitInsn(Opcodes.ACONST_NULL); break;
        }
        mv.visitInsn(ret.getOpcode(Opcodes.IRETURN));
        mv.visitMaxs(4, 4);
    }
    private static final class HarnessLoader extends ClassLoader {
        private final String target;
        private final byte[] bytes;
        private final URLClassLoader cp;
        HarnessLoader(URLClassLoader cp, String target, byte[] bytes) {
            super(ClassLoader.getPlatformClassLoader());
            this.cp = cp; this.target = target; this.bytes = bytes;
        }
        Class<?> loadAndInit() {
            try {
                Class<?> c = loadClass(target, true);
                return c;
            } catch (Throwable t) {
                return null;
            }
        }
        @Override protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    if (name.equals(target)) {
                        c = defineClass(name, bytes, 0, bytes.length);
                    } else {
                        try {
                            c = cp.loadClass(name);
                        } catch (ClassNotFoundException e) {
                            c = super.loadClass(name, false);
                        }
                    }
                }
                if (resolve) resolveClass(c);
                return c;
            }
        }
    }
    private static byte[] readFromLoader(URLClassLoader loader, String internal) throws IOException {
        URL u = loader.findResource(internal + ".class");
        if (u == null) return null;
        try (InputStream in = u.openStream()) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
            return bos.toByteArray();
        }
    }
    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
