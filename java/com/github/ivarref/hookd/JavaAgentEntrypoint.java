package com.github.ivarref.hookd;

import java.lang.instrument.Instrumentation;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.ivarref.hookd.JavaAgent.transformClass;
import static com.github.ivarref.hookd.State.*;

public class JavaAgentEntrypoint {

    private static final Logger LOGGER = Logger.getLogger(JavaAgentEntrypoint.class.getName());

    public static void premain(String agentArgs, Instrumentation inst) {
        LOGGER.log(Level.INFO, "Agent premain doing nothing");
    }

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
}
