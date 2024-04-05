package com.github.ivarref.hookd;

import java.util.HashMap;
import java.util.function.Consumer;

public class PreFunctionInvoke {

    public static Object callback(String op, Object fromObj, String clazz, String method, String id, Long startTime, Long stopTime, Object[] args, Object retVal) {
        JavaAgent.consumePrePost(op, fromObj, clazz, method, args, retVal);
        if (!JavaAgent.prePost.containsKey(clazz)) {
            throw new RuntimeException("Could not find prepost handler for class: " + clazz);
        }

        JavaAgent.TransformConfig cfg = JavaAgent.prePost.get(clazz);
        if (!cfg.prePostConsumers.containsKey(method)) {
            throw new RuntimeException("Could not find prepost handler for class and method: " + clazz + " : " + method);
        }
        Consumer consumer = cfg.prePostConsumers.get(method);

        boolean isPre = "pre".equalsIgnoreCase(op);

        HashMap<String, Object> map = new HashMap<>();
        map.put("pre?", isPre);
        map.put("post?", !isPre);
        map.put("start", startTime.longValue());
        map.put("args", args);
        map.put("id", id);

        if (!isPre) {
            map.put("stop", stopTime.longValue());
            map.put("result", retVal);
        }

        consumer.accept(map);

        return null;
    }
}
