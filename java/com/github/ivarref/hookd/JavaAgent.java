package com.github.ivarref.hookd;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import javassist.*;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentHashMap;
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
    public static final AtomicReference<String> methodNameInput = new AtomicReference();

    public static final AtomicReference<CountDownLatch> latch = new AtomicReference<>();

    public static void agentmain(String agentArgs, Instrumentation inst) {
        try {
            transformClass(classNameInput.get(), methodNameInput.get(), inst);
        } finally {
            latch.get().countDown();
        }
    }

    public static ConcurrentHashMap<String, Consumer> consumers = new ConcurrentHashMap<>();

    public static synchronized void addPostHook(String clazzName, String methodName, Consumer consumer) throws IOException, AttachNotSupportedException, AgentLoadException, AgentInitializationException, InterruptedException {
        String k = clazzName + "::" + methodName;
        if (consumers.containsKey(k)) {
            consumers.put(k, consumer);
        } else {
            CountDownLatch theLatch = new CountDownLatch(1);
            latch.set(theLatch);
            long pid = ProcessHandle.current().pid();
            classNameInput.set(clazzName);
            methodNameInput.set(methodName);
            consumers.put(k, consumer);
            VirtualMachine jvm = VirtualMachine.attach(pid + "");
            jvm.loadAgent(JavaAgent.class.getProtectionDomain().getCodeSource().getLocation().getFile());
            jvm.detach();
            if (false == theLatch.await(30, TimeUnit.SECONDS)) {
                String msg = "Timeout waiting for JavaAgent to finish";
                LOGGER.log(Level.SEVERE, msg);
                throw new RuntimeException(msg);
            }
        }
    }

    public static void consume(String clazzName, String methodName, Object res) {
        Consumer consumer = consumers.get(clazzName + "::" + methodName);
        if (consumer != null) {
            consumer.accept(res);
        } else {
            System.out.println("Agent consume error: No consumer registered for " + clazzName + "/" + methodName);
        }
    }

    public static void transformClass(String className, String methodName, Instrumentation inst) {
        LOGGER.log(Level.FINEST, "transformClass starting for " + className + "/" + methodName);
        Class<?> targetCls = null;
        ClassLoader targetClassLoader = null;
        try {
            targetCls = Class.forName(className);
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, "Agent: class not found with Class.forName");
        }
        if (targetCls != null) {
            targetClassLoader = targetCls.getClassLoader();
            transform(targetCls, methodName, targetClassLoader, inst);
            return;
        }
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            if (clazz.getName().equals(className)) {
                targetCls = clazz;
                targetClassLoader = targetCls.getClassLoader();
                transform(targetCls, methodName, targetClassLoader, inst);
                return;
            }
        }
        throw new RuntimeException("Agent failed to find class [" + className + "]");
    }

    public static void transform(Class<?> clazz, String methodName, ClassLoader classLoader, Instrumentation instrumentation) {
        ClassTransformer dt = new ClassTransformer(clazz.getName(), methodName, classLoader);
        instrumentation.addTransformer(dt, true);
        try {
            instrumentation.retransformClasses(clazz);
        } catch (Throwable ex) {
            throw new RuntimeException("Agent transform failed for: [" + clazz.getName() + "]", ex);
        }
    }

    public static class ClassTransformer implements ClassFileTransformer {
        private final String targetClassName;
        private final ClassLoader targetClassLoader;
        private final String targetMethodName;

        public ClassTransformer(String targetClassName, String methodName, ClassLoader targetClassLoader) {
            this.targetClassName = targetClassName;
            this.targetClassLoader = targetClassLoader;
            this.targetMethodName = methodName;
        }

        @Override
        public byte[] transform(
                ClassLoader loader,
                String className,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer) {
            byte[] byteCode = classfileBuffer;
            String finalTargetClassName = this.targetClassName.replaceAll("\\.", "/");
            if (!className.equals(finalTargetClassName)) {
                return byteCode;
            }
            ClassPool.getDefault().insertClassPath(new LoaderClassPath(loader));
            ClassPool.getDefault().appendSystemPath();
            if (className.equals(finalTargetClassName)
                    && loader.equals(targetClassLoader)) {
                try {
                    ClassPool cp = ClassPool.getDefault();
                    String clazz = targetClassName;
                    CtClass cc = cp.get(clazz);
                    if (targetMethodName.equalsIgnoreCase("::Constructor")) {
                        for (CtConstructor m : cc.getConstructors()) {
                            m.insertAfter("com.github.ivarref.spa.JavaAgent.consume(\"" + this.targetClassName + "\"," +
                                    "\"" + this.targetMethodName + "\"" +
                                    ",this);");
                        }
                    } else {
                        CtMethod m = cc.getDeclaredMethod(targetMethodName);
                        m.addLocalVariable("startTime", CtClass.longType);
                        m.insertBefore("startTime = System.currentTimeMillis();");
                        StringBuilder endBlock = new StringBuilder();
                        m.addLocalVariable("endTime", CtClass.longType);
                        endBlock.append("endTime = System.currentTimeMillis();");
                        endBlock.append("com.github.ivarref.spa.JavaAgent.consume(\"" + this.targetClassName + "\"," +
                                "\"" + this.targetMethodName + "\"" +
                                ",$_);");
                        m.insertAfter(endBlock.toString());
                    }
                    byteCode = cc.toBytecode();
                    cc.detach();
                    LOGGER.log(Level.FINE, "Transformed class " + this.targetClassName + "/" + this.targetMethodName);
                } catch (Throwable e) {
                    if (e instanceof NotFoundException) {
                        LOGGER.log(Level.SEVERE, "Agent did not find: " + e.getMessage(), e);
                    } else if (e instanceof CannotCompileException) {
                        LOGGER.log(Level.SEVERE, "Agent could not compile: " + e.getMessage(), e);
                    } else {
                        LOGGER.log(Level.SEVERE, "Transforming class " + this.targetClassName + " failed. Message: " + e.getMessage() + ", class: " + e.getClass().getName(), e);
                    }
                    throw new RuntimeException(e);
                }
            }
            return byteCode;
        }
    }
}
