package com.github.ivarref.hookd;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class PreFunctionInvoke {

    public static Object callback(String op, Object fromObj, String clazz, String method, Object[] args, long startTime, long stopTime, Object retVal) {
        JavaAgent.TransformConfig cfg = JavaAgent.prePost.get(clazz);
        Consumer methodPreConsumer = cfg.prePostConsumers.get(method);
        Map<String, Object> map = new HashMap<>();
        map.put("args", args);
        map.put("start", startTime);
        map.put("this", fromObj);

        if (op.equalsIgnoreCase("pre")) {
            map.put("pre?", true);
            map.put("post?", false);
            methodPreConsumer.accept(map);
        } else if (op.equalsIgnoreCase("post")) {
            map.put("pre?", false);
            map.put("post?", true);
            map.put("stop", stopTime);
            map.put("result", retVal);
            methodPreConsumer.accept(map);
        }
        return null;
    }
}
