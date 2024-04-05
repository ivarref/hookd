package com.github.ivarref.hookd;

import com.sun.tools.attach.VirtualMachine;
import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class JavaAgent {

    private static final Logger LOGGER = Logger.getLogger(JavaAgent.class.getName());

    public static void premain(String agentArgs, Instrumentation inst) {
        LOGGER.log(Level.INFO, "Agent premain doing nothing");
    }

    public static final AtomicReference<String> classNameInput = new AtomicReference();
    public static final AtomicReference<CountDownLatch> latch = new AtomicReference<>();
    public static final AtomicReference<Throwable> error = new AtomicReference<>();

    public static void agentmain(String agentArgs, Instrumentation inst) {
        try {
            error.set(null);
            transformClass(classNameInput.get(), inst, getIgnoresClasses());
        } catch (Throwable t) {
            error.set(t);
        } finally {
            latch.get().countDown();
        }
    }

    public static final ConcurrentHashMap<String, TransformConfig> ret = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, TransformConfig> retMod = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, TransformConfig> pre = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, TransformConfig> prePost = new ConcurrentHashMap<>();

    public static class TransformConfig {
        public final ConcurrentHashMap<String, BiConsumer> consumers = new ConcurrentHashMap<>();

        public final ConcurrentHashMap<String, Consumer> prePostConsumers = new ConcurrentHashMap<>();

        public final ConcurrentHashMap<String, BiFunction> modifiers = new ConcurrentHashMap<>();
    }

    public static void clear(String clazz) throws Throwable {
        if (ret.containsKey(clazz)) {
            ret.remove(clazz);
        }
        if (pre.containsKey(clazz)) {
            pre.remove(clazz);
        }
        if (retMod.containsKey(clazz)) {
            retMod.remove(clazz);
        }
        attachAndTransform(clazz);
    }

    public static void attachAndTransform(String clazz) throws Throwable {
        latch.set(new CountDownLatch(1));
        classNameInput.set(clazz);
        VirtualMachine jvm = VirtualMachine.attach(ProcessHandle.current().pid() + "");
        jvm.loadAgent(JavaAgent.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        jvm.detach();
        if (false == latch.get().await(30, TimeUnit.SECONDS)) {
            String msg = "Timeout waiting for JavaAgent to finish";
            LOGGER.log(Level.SEVERE, msg);
            throw new RuntimeException(msg);
        } else {
            if (error.get() != null) {
                throw error.get();
            }
        }
    }

    public static synchronized void addReturnConsumer(String clazzName, String methodName, Consumer consumer) throws Throwable {
        if (!ret.containsKey(clazzName)) {
            ret.put(clazzName, new TransformConfig());
        }
        TransformConfig classConfig = ret.get(clazzName);
        classConfig.consumers.put(methodName, (o, o2) -> consumer.accept(o2));
        attachAndTransform(clazzName);
    }

    public static synchronized void addReturnModifier(String clazzName, String methodName, BiFunction f) throws Throwable {
        if (clazzName.equalsIgnoreCase("java.lang.System") && methodName.equalsIgnoreCase("currentTimeMillis")) {
            clazzName = "NATIVE_" + clazzName;
        }
        if (!retMod.containsKey(clazzName)) {
            retMod.put(clazzName, new TransformConfig());
        }
        TransformConfig classConfig = retMod.get(clazzName);
        classConfig.modifiers.put(methodName, f);
        attachAndTransform(clazzName);
    }

    public static synchronized void addPreHook(String clazzName, String methodName, BiConsumer consumer) throws Throwable {
        if (!pre.containsKey(clazzName)) {
            pre.put(clazzName, new TransformConfig());
        }
        TransformConfig classConfig = pre.get(clazzName);
        classConfig.consumers.put(methodName, consumer);
        attachAndTransform(clazzName);
    }

    public static synchronized void addPrePost(String clazzName, String methodName, Consumer consumer) throws Throwable {
        if (!prePost.containsKey(clazzName)) {
            prePost.put(clazzName, new TransformConfig());
        }
        TransformConfig classConfig = prePost.get(clazzName);
        classConfig.prePostConsumers.put(methodName, consumer);
        attachAndTransform(clazzName);
    }

    public static void consumeReturn(String clazzName, String methodName, Object res) {
        BiConsumer consumer = ret.get(clazzName).consumers.get(methodName);
        if (consumer != null) {
            consumer.accept(null, res);
        } else {
            LOGGER.log(Level.SEVERE, "Agent consumeReturn error. No consumeReturn registered for " + clazzName + "/" + methodName);
        }
    }

    public static Object modifyReturn(String clazzName, String methodName, Object[] args, Object res) {
        BiFunction f = retMod.get(clazzName).modifiers.get(methodName);
        if (f != null) {
            return f.apply(args, res);
        } else {
            LOGGER.log(Level.SEVERE, "Agent modifyReturn error. No consumeReturn registered for " + clazzName + "/" + methodName);
            return null;
        }
    }

    public static void consumePre(String clazzName, String methodName, Object t, Object[] args) {
        BiConsumer consumer = pre.get(clazzName).consumers.get(methodName);
        if (consumer != null) {
            consumer.accept(t, args);
        } else {
            LOGGER.log(Level.SEVERE, "Agent preConsume error. No preConsume registered for " + clazzName + "/" + methodName);
        }
    }

    public static void consumePrePost(String op, Object fromObj, String clazz, String method, Object[] args, Object retVal) {

    }

    public static Set<String> getIgnoresClasses() {
        TreeSet<String> s = new TreeSet<>();
        try (BufferedReader fr = new BufferedReader(new FileReader("ignoreclasses.txt"))) {
            while (true) {
                String line = fr.readLine();
                if (null == line) {
                    break;
                }
                s.add(line);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return s;
    }

    public static String simpleClassName(Class clazz) {
        return clazz.getSimpleName().split(Pattern.quote("/"))[0];
    }

    public static void addIgnoreClass(Class clazz) {
        if (getIgnoresClasses().contains(simpleClassName(clazz))) {
            return;
        }
        try (FileWriter fw = new FileWriter("ignoreclasses.txt", true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(simpleClassName(clazz));
            bw.newLine();
            LOGGER.log(Level.FINE, "Added " + clazz.getSimpleName() + " to ignored classes");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void transformClass(String className, Instrumentation inst, Set<String> ignoreClasses) {
        LOGGER.log(Level.FINE, "transformClass starting for " + className);
        Class<?> targetCls = null;
        ClassLoader targetClassLoader = null;
        try {
            targetCls = Class.forName(className);
        } catch (Throwable ex) {
            if (!className.startsWith("NATIVE_")) {
                LOGGER.log(Level.WARNING, "Agent: class not found with Class.forName for class: " + className);
            }
        }
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            if (!clazz.getName().startsWith("com.github.ivarref.hookd") && !ignoreClasses.contains(simpleClassName(clazz))) {
                try {
                    transform(className, clazz, clazz.getClassLoader(), inst);
                } catch (Throwable t) {
                    if ("java.lang.instrument.UnmodifiableClassException".equalsIgnoreCase(t.getMessage())) {
                        LOGGER.log(Level.FINE, "Transform failed for class: " + clazz.getSimpleName() + " : " + t.getMessage());
                        addIgnoreClass(clazz);
                    } else {
                        System.err.println("Transform failed for class: " + clazz.getSimpleName() + " : " + t.getMessage());
                    }
                }
            }
        }
    }

    public static final ConcurrentHashMap<String, ClassTransformer> transformers = new ConcurrentHashMap<>();

    public static final ConcurrentSkipListSet<String> okTransform = new ConcurrentSkipListSet<>();
    public static final ConcurrentSkipListSet<String> errorTransform = new ConcurrentSkipListSet<>();

    public static void transform(String originClass, Class<?> clazz, ClassLoader classLoader, Instrumentation instrumentation) {
        if (false == transformers.containsKey(clazz.getName())) {
            ClassTransformer dt = new ClassTransformer(clazz.getName(), classLoader);
            transformers.put(clazz.getName(), dt);
            instrumentation.addTransformer(dt, true);
        }
        try {
            instrumentation.retransformClasses(clazz);
            okTransform.add(clazz.getName());
        } catch (UnmodifiableClassException uce) {
            errorTransform.add(clazz.getName());
            if (originClass.startsWith("NATIVE_")) {
                LOGGER.log(Level.FINE, "UnmodifiableClassException for class " + clazz.getName());
            } else {
                throw new RuntimeException(uce);
            }
        } catch (Throwable ex) {
            errorTransform.add(clazz.getName());
            throw new RuntimeException("Agent transform failed for: [" + clazz.getName() + "]", ex);
        }
    }

    public static void info(String msg) {
        LOGGER.log(Level.INFO, msg);
    }

    public static class ClassTransformer implements ClassFileTransformer {
        private final String targetClassName;
        private final ClassLoader targetClassLoader;

        public ClassTransformer(String targetClassName, ClassLoader targetClassLoader) {
            this.targetClassName = targetClassName;
            this.targetClassLoader = targetClassLoader;
        }

        public byte[] throwingTransform(
                ClassLoader loader,
                String className,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] byteCode) throws Exception {
            String finalTargetClassName = this.targetClassName.replaceAll("\\.", "/");
            if (!className.equals(finalTargetClassName)) {
                return byteCode;
            }
            if (loader != null && targetClassLoader != null && !loader.equals(targetClassLoader)) {
                return byteCode;
            }
            if (targetClassName.startsWith("com.github.ivarref.hookd")) {
                return byteCode;
            }

            ClassPool.getDefault().insertClassPath(new LoaderClassPath(loader));
            ClassPool.getDefault().insertClassPath(new ByteArrayClassPath(targetClassName, byteCode));
            ClassPool.getDefault().appendSystemPath();
            ClassPool pool = ClassPool.getDefault();

            CtClass cc = pool.get(targetClassName);

            if (ret.containsKey(targetClassName)) {
                for (String method : ret.get(targetClassName).consumers.keySet()) {
                    if (method.equalsIgnoreCase("::Constructor")) {
                        for (CtConstructor m : cc.getConstructors()) {
                            m.insertAfter("com.github.ivarref.hookd.JavaAgent.consumeReturn(\"" + this.targetClassName + "\"," + "\"" + method + "\"" + ",this);");
                        }
                    } else {
                        CtMethod m = cc.getDeclaredMethod(method);
                        String self = ((m.getModifiers() & Modifier.STATIC) != 0) ? "null" : "this";
                        m.addLocalVariable("clazz", pool.get(Class.class.getName()));
                        String getClazz = "clazz = java.lang.Class.forName(\"com.github.ivarref.hookd.CallbackFunction\", true, java.lang.Thread.currentThread().getContextClassLoader());";
                        m.insertAfter(getClazz + " clazz.getMethods()[0].invoke(null, new Object[] {\"ret\", " + self + ", \"" + this.targetClassName + "\", \"" + method + "\", $args, $_});");
                    }
                }
            }

            if (retMod.containsKey(targetClassName)) {
                for (String method : retMod.get(targetClassName).modifiers.keySet()) {
                    CtMethod m = cc.getDeclaredMethod(method);
                    String self = ((m.getModifiers() & Modifier.STATIC) != 0) ? "null" : "this";
                    m.addLocalVariable("clazz", pool.get(Class.class.getName()));
                    String getClazz = "clazz = java.lang.Class.forName(\"com.github.ivarref.hookd.CallbackFunction\", true, java.lang.Thread.currentThread().getContextClassLoader());";
                    m.insertAfter(getClazz + " $_ = clazz.getMethods()[0].invoke(null, new Object[] {\"retMod\", " + self + ", \"" + this.targetClassName + "\", \"" + method + "\", $args, $_});");
                }
            }

            TransformConfig jlSystem = retMod.getOrDefault("NATIVE_java.lang.System", new TransformConfig());
            boolean overrideCurrentTimeMillis = jlSystem.modifiers.containsKey("currentTimeMillis");
            if (overrideCurrentTimeMillis) {
                cc.instrument(new ExprEditor() {
                    @Override
                    public void edit(MethodCall m) throws CannotCompileException {
                        String className = m.getClassName();
                        String method = m.getMethodName();
                        // from: https://stackoverflow.com/questions/12663905/editing-a-native-method-class-with-javassist
                        if (className == null || method == null) {
                            return;
                        }
                        // more examples: https://www.tabnine.com/code/java/classes/javassist.expr.MethodCall
                        if (overrideCurrentTimeMillis && className.equals("java.lang.System") && method.equals("currentTimeMillis")) {
                            m.replace("{ Class clazz = java.lang.Class.forName(\"com.github.ivarref.hookd.NativeOverride\", true, java.lang.Thread.currentThread().getContextClassLoader());" +
                                    " Object res = clazz.getDeclaredMethod(\"currentTimeMillis\", new java.lang.Class[0]).invoke(null, new java.lang.Object[0]);" +
                                    " $_ = ((Long)res).longValue(); }");
                        }
                    }
                });
            }

            if (prePost.containsKey(targetClassName)) {
                for (String method : prePost.get(targetClassName).prePostConsumers.keySet()) {
                    if (method.equalsIgnoreCase("::Constructor")) {
                    } else {
                        for (CtMethod m : cc.getMethods()) {
                            if (m.getName().equals(method)) {
                                StringBuilder beforeBlock = new StringBuilder();
                                StringBuilder endBlock = new StringBuilder();
                                String self = ((m.getModifiers() & Modifier.STATIC) != 0) ? "null" : "this";

                                m.addLocalVariable("startTime", pool.get("java.lang.Long"));
                                m.addLocalVariable("stopTime", pool.get("java.lang.Long"));
                                m.addLocalVariable("id", pool.get("java.lang.String"));
                                m.addLocalVariable("method", pool.get(Method.class.getName()));

                                beforeBlock.append("method = java.lang.Class.forName(\"com.github.ivarref.hookd.PreFunctionInvoke\", true, java.lang.Thread.currentThread().getContextClassLoader()).getMethods()[0];");
                                beforeBlock.append("startTime = Long.valueOf(System.nanoTime());");
                                beforeBlock.append("id = java.util.UUID.randomUUID().toString();");
                                beforeBlock.append("method.invoke(null, new Object[] {\"pre\", " + self + ", \"" + this.targetClassName + "\", \"" + method + "\", id, startTime, null, $args, null});");

                                endBlock.append("stopTime = Long.valueOf(System.nanoTime());");
                                endBlock.append("method.invoke(null, new Object[] {\"post\", " + self + ", \"" + this.targetClassName + "\", \"" + method + "\", id, startTime, stopTime, $args, $_});");
                                m.insertBefore(beforeBlock.toString());
                                m.insertAfter(endBlock.toString());

                                m.addCatch("{" +
                                        "java.lang.Class.forName(\"com.github.ivarref.hookd.PreFunctionInvoke\", true, java.lang.Thread.currentThread().getContextClassLoader()).getMethods()[0]" +
                                        ".invoke(null, new Object[] {\"post\", " + self + ", \"" + this.targetClassName + "\", \"" + method + "\", id, startTime, Long.valueOf(System.nanoTime()), $args, t});" +
                                        "System.err.println(\"oh noes!\"); throw t; }", pool.get("java.lang.Throwable"), "t");
                            }
                        }
                    }
                }
            }

            if (pre.containsKey(targetClassName)) {
                for (String method : pre.get(targetClassName).consumers.keySet()) {
                    if (method.equalsIgnoreCase("::Constructor")) {
                        for (CtConstructor m : cc.getConstructors()) {
                            m.insertBefore("com.github.ivarref.hookd.JavaAgent.consumePre(\"" + this.targetClassName + "\"," + "\"" + method + "\"" + ", $0, $args);");
                        }
                    } else {
                        CtMethod m = cc.getDeclaredMethod(method);
                        String self = ((m.getModifiers() & Modifier.STATIC) != 0) ? "null" : "this";
                        m.addLocalVariable("clazz", pool.get(Class.class.getName()));
                        String getClazz = "clazz = java.lang.Class.forName(\"com.github.ivarref.hookd.CallbackFunction\", true, java.lang.Thread.currentThread().getContextClassLoader());";
                        m.insertBefore(getClazz + " clazz.getMethods()[0].invoke(null, new Object[] {\"pre\", " + self + ", \"" + this.targetClassName + "\", \"" + method + "\", $args, null});");
                    }
                }
            }
            byteCode = cc.toBytecode();
            cc.detach();
            return byteCode;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            try {
                return throwingTransform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Could not transform class " + className, e);
                throw new RuntimeException(e);
            }
        }
    }
}
