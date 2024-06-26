package com.github.ivarref.hookd;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Callback {
    public static final ThreadLocal<ArrayDeque<Map<String, Object>>> threadVars = ThreadLocal.withInitial(() -> new ArrayDeque<>());
    private static final Logger LOGGER = Logger.getLogger(Callback.class.getName());;

    public static Consumer getConsumer(String clazz, String method) {
        if (!JavaAgent.prePost.containsKey(clazz)) {
            throw new RuntimeException("Could not find prepost handler for class: " + clazz);
        }

        JavaAgent.TransformConfig cfg = JavaAgent.prePost.get(clazz);
        if (!cfg.prePostConsumers.containsKey(method)) {
            throw new RuntimeException("Could not find prepost handler for class and method: " + clazz + " : " + method);
        }
        return cfg.prePostConsumers.get(method);
    }

    public static void enterPre(Object fromObj, String clazz, String method, String id, Long startTime, Object[] args) {
        Consumer consumer = getConsumer(clazz, method);

        HashMap<String, Object> map = new HashMap<>();
        map.put("pre?", true);
        map.put("post?", false);
        map.put("start", startTime.longValue());
        map.put("args", args);
        map.put("id", id);
        map.put("this", fromObj);

        threadVars.get().add(map);

        try {
            consumer.accept(map);
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "pre callback failed with message: " + t.getMessage(), t);
            throw t;
        }
    }

    public static void enterPost(Object fromObj, String clazz, String method, Long stopTime, Object[] args, boolean isConstructor, Object retVal) {
        Consumer consumer = getConsumer(clazz, method);
        HashMap<String, Object> map = new HashMap<>();
        map.putAll(threadVars.get().removeLast());
        map.put("pre?", false);
        map.put("post?", true);
        map.put("stop", stopTime.longValue());
        map.put("args", args);
        map.put("this", fromObj);
        map.put("result", isConstructor ? fromObj : retVal);

        try {
            consumer.accept(map);
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "post callback failed with message: " + t.getMessage(), t);
            throw t;
        }
    }

    public static void enterException(Object fromObj, String clazz, String method, Long stopTime, Object[] args, Throwable tt) {
        Consumer consumer = getConsumer(clazz, method);
        HashMap<String, Object> map = new HashMap<>();
        map.putAll(threadVars.get().removeLast());
        map.put("pre?", false);
        map.put("post?", true);
        map.put("error?", true);
        map.put("stop", stopTime.longValue());
        map.put("this", fromObj);
        map.put("args", args);
        map.put("error", tt);

        try {
            consumer.accept(map);
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "post exception callback failed with message: " + t.getMessage(), t);
            throw t;
        }
    }
}
