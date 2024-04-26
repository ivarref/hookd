package com.github.ivarref.hookd;

import com.sun.tools.attach.VirtualMachine;
import javassist.*;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

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
            transformClass(classNameInput.get(), inst);
        } catch (Throwable t) {
            error.set(t);
        } finally {
            latch.get().countDown();
        }
    }

    public static final ConcurrentHashMap<String, TransformConfig> prePost = new ConcurrentHashMap<>();

    public static class TransformConfig {
        public final ConcurrentHashMap<String, Consumer> prePostConsumers = new ConcurrentHashMap<>();
    }

    public static void clear(String clazz) throws Throwable {
        if (prePost.containsKey(clazz)) {
            prePost.remove(clazz);
        }
        attachAndTransform(clazz);
    }

    public static void clearAll() throws Throwable {
        List<String> clazzes = new ArrayList<>(prePost.keySet());
        for (String clazz : clazzes) {
            clear(clazz);
        }
    }

    public static void attachAndTransform(String clazz) throws Throwable {
        latch.set(new CountDownLatch(1));
        classNameInput.set(clazz);
        VirtualMachine jvm = VirtualMachine.attach(ProcessHandle.current().pid() + "");
        File tempFile = File.createTempFile("hookd-agent-", "jar");
        try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(tempFile));
             InputStream in = new BufferedInputStream(JavaAgent.class.getClassLoader().getResourceAsStream("com/github/ivarref/hookd.bin.jar"))) {
            in.transferTo(fos);
            fos.flush();
            jvm.loadAgent(tempFile.getAbsolutePath());
            jvm.detach();
            if (false == latch.get().await(60, TimeUnit.SECONDS)) {
                String msg = "Timeout waiting for JavaAgent to finish";
                LOGGER.log(Level.SEVERE, msg);
                throw new RuntimeException(msg);
            } else {
                if (error.get() != null) {
                    throw error.get();
                }
            }
        } finally {
            try {
                tempFile.delete();
            } catch (Throwable t) {

            }
        }
    }

    public static synchronized void addPrePost(String clazzName, String methodName, Consumer consumer) throws Throwable {
        if (!prePost.containsKey(clazzName)) {
            prePost.put(clazzName, new TransformConfig());
        }
        TransformConfig classConfig = prePost.get(clazzName);
        classConfig.prePostConsumers.put(methodName, consumer);
        attachAndTransform(clazzName);
    }

    public static void transformClass(String className, Instrumentation inst) throws Throwable {
        LOGGER.log(Level.FINE, "transformClass starting for " + className);
        Class<?> targetCls = null;
        ClassLoader targetClassLoader = null;
        try {
            targetCls = Class.forName(className);
        } catch (Throwable ex) {
            throw new RuntimeException("hookd JavaAgent: class not found with Class.forName for class:" + className);
        }

        if (targetCls != null) {
            targetClassLoader = targetCls.getClassLoader();
            transform(className, targetCls, targetClassLoader, inst);
        }
    }

    public static final ConcurrentHashMap<String, ClassTransformer> transformers = new ConcurrentHashMap<>();

    public static final ConcurrentSkipListSet<String> okTransform = new ConcurrentSkipListSet<>();
    public static final ConcurrentSkipListSet<String> errorTransform = new ConcurrentSkipListSet<>();

    public static void transform(String originClass, Class<?> clazz, ClassLoader classLoader, Instrumentation instrumentation) throws Throwable {
        boolean initial = false == transformers.containsKey(clazz.getName());
        ClassTransformer dt = transformers.computeIfAbsent(clazz.getName(), s -> new ClassTransformer(clazz.getName(), classLoader));
        dt.transformError.set(null);
        try {
            if (initial) {
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
                if (ex instanceof RuntimeException) {
                    throw ex;
                } else {
                    throw new RuntimeException("Agent transform failed for: [" + clazz.getName() + "]", ex);
                }
            }
        } finally {
            Throwable tError = dt.transformError.get();
            if (tError != null) {
                throw tError;
            }
        }
    }

    public static void info(String msg) {
        LOGGER.log(Level.INFO, msg);
    }

    public static class ClassTransformer implements ClassFileTransformer {
        private final String targetClassName;
        private final ClassLoader targetClassLoader;
        public final AtomicReference<Throwable> transformError = new AtomicReference<>();

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

            if (prePost.containsKey(targetClassName)) {
                for (String method : prePost.get(targetClassName).prePostConsumers.keySet()) {
                    boolean found = false;
                    for (CtConstructor c : cc.getConstructors()) {
                        if (c.getName().equals(method)) {
                            addPrePostHandler(this.targetClassName, method, c, pool);
                            found = true;
                        }
                    }
                    for (CtMethod m : cc.getMethods()) {
                        if (m.getName().equals(method)) {
                            addPrePostHandler(this.targetClassName, method, m, pool);
                            found = true;
                        }
                    }
                    if (!found) {
                        throw new RuntimeException("hookd JavaAgent: method " + method + " not found for class " + this.targetClassName);
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
            } catch (Throwable t) {
                if ("true".equalsIgnoreCase(System.getProperty("hookd.stacktraces", "true"))) {
                    LOGGER.log(Level.SEVERE, "Could not transform class " + className + ": " + t.getMessage(), t);
                } else {
                    LOGGER.log(Level.SEVERE, "Could not transform class " + className + ": " + t.getMessage());
                }
                transformError.set(t);
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else {
                    throw new RuntimeException(t);
                }
            }
        }
    }

    public static void addPrePostHandler(String targetClassName, String method, CtBehavior m, ClassPool pool) throws CannotCompileException, NotFoundException {
        String self = ((m.getModifiers() & Modifier.STATIC) != 0) ? "null" : "this";
        String beforeBlock = "java.lang.Class.forName(\"com.github.ivarref.hookd.PreFunctionInvoke\", true, java.lang.Thread.currentThread().getContextClassLoader()).getMethods()[0]"
                + ".invoke(null, new Object[] {" + self + ", \"" + targetClassName + "\", \"" + method + "\", java.util.UUID.randomUUID().toString(), Long.valueOf(System.nanoTime()), $args});";
        String isConstructor = m instanceof CtConstructor ? "Boolean.TRUE" : "Boolean.FALSE";
        String afterBlock = "java.lang.Class.forName(\"com.github.ivarref.hookd.PostFunctionInvoke\", true, java.lang.Thread.currentThread().getContextClassLoader()).getMethods()[0]"
                + ".invoke(null, new Object[] {" + self + ", \"" + targetClassName + "\", \"" + method + "\", Long.valueOf(System.nanoTime()), $args, " + isConstructor + ", ($w)$_});";
        if (m instanceof CtConstructor) {
            CtConstructor c = (CtConstructor) m;
            c.insertBeforeBody(beforeBlock);
        } else {
            m.insertBefore(beforeBlock);
        }
        m.insertAfter(afterBlock);
        m.addCatch("{" +
                "java.lang.Class.forName(\"com.github.ivarref.hookd.ExceptionFunctionInvoke\", true, java.lang.Thread.currentThread().getContextClassLoader()).getMethods()[0]" +
                ".invoke(null, new Object[] {" + self
                + ", \"" + targetClassName
                + "\", \"" + method
                + "\", Long.valueOf(System.nanoTime()), $args, t});" +
                "throw t; }", pool.get("java.lang.Throwable"), "t");
    }
}
