package com.github.ivarref.hookd;

import com.sun.tools.attach.VirtualMachine;
import javassist.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.ivarref.hookd.State.*;

public class JavaAgent {

    private static final Logger LOGGER = Logger.getLogger(JavaAgent.class.getName());

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
        jvm.loadAgent(JavaAgent.class.getProtectionDomain().getCodeSource().getLocation().getFile());
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
    }

    public static synchronized void addPrePost(String clazzName, String methodName, Consumer consumer) throws Throwable {
        if (!prePost.containsKey(clazzName)) {
            prePost.put(clazzName, new TransformConfig());
        }
        TransformConfig classConfig = prePost.get(clazzName);
        classConfig.prePostConsumers.put(methodName, consumer);
        attachAndTransform(clazzName);
    }

    public static void transformClass(String className, Instrumentation inst) {
        LOGGER.log(Level.FINE, "transformClass starting for " + className);
        Class<?> targetCls = null;
        ClassLoader targetClassLoader = null;
        try {
            targetCls = Class.forName(className);
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, "Agent: class not found with Class.forName for class: " + className);
        }

        if (targetCls != null) {
            targetClassLoader = targetCls.getClassLoader();
            transform(className, targetCls, targetClassLoader, inst);
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
            ex.printStackTrace();
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

            if (prePost.containsKey(targetClassName)) {
                for (String method : prePost.get(targetClassName).prePostConsumers.keySet()) {
                    for (CtMethod m : cc.getMethods()) {
                        if (m.getName().equals(method)) {
                            addPrePostHandler(this.targetClassName, method, m, pool);
                        }
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

    public static void addPrePostHandler(String targetClassName, String method, CtMethod m, ClassPool pool) throws CannotCompileException, NotFoundException {
        StringBuilder beforeBlock = new StringBuilder();
        String self = ((m.getModifiers() & Modifier.STATIC) != 0) ? "null" : "this";

        m.addLocalVariable("startTime", pool.get("java.lang.Long"));
        m.addLocalVariable("stopTime", pool.get("java.lang.Long"));
        m.addLocalVariable("id", pool.get("java.lang.String"));
        m.addLocalVariable("method", pool.get(Method.class.getName()));

        beforeBlock.append("method = java.lang.Class.forName(\"com.github.ivarref.hookd.PreFunctionInvoke\", true, java.lang.Thread.currentThread().getContextClassLoader()).getMethods()[0];");
        beforeBlock.append("startTime = Long.valueOf(System.nanoTime());");
        beforeBlock.append("id = java.util.UUID.randomUUID().toString();");
        beforeBlock.append("method.invoke(null, new Object[] {" + self + ", \"" + targetClassName + "\", \"" + method + "\", id, startTime, $args});");

        m.insertBefore(beforeBlock.toString());
        m.insertAfter("stopTime = Long.valueOf(System.nanoTime()); "
                + "java.lang.Class.forName(\"com.github.ivarref.hookd.PostFunctionInvoke\", true, java.lang.Thread.currentThread().getContextClassLoader()).getMethods()[0]"
                + ".invoke(null, new Object[] {" + self + ", \"" + targetClassName + "\", \"" + method + "\", id, startTime, stopTime, $args, ($w)$_});");
        m.addCatch("{" +
                "java.lang.Class.forName(\"com.github.ivarref.hookd.ExceptionFunctionInvoke\", true, java.lang.Thread.currentThread().getContextClassLoader()).getMethods()[0]" +
                ".invoke(null, new Object[] {" + self
                + ", \"" + targetClassName
                + "\", \"" + method
                + "\", Long.valueOf(System.nanoTime()), $args, t});" +
                "throw t; }", pool.get("java.lang.Throwable"), "t");
    }
}
