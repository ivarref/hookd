package com.github.ivarref.hookd;

public class CallbackFunction {

    public static void callback(String op, Object fromObj, String clazz, String method, Object[] args, Object retVal) {
        if (op.equalsIgnoreCase("pre")) {
            JavaAgent.consumePre(clazz, method, fromObj, args);
        } else if (op.equalsIgnoreCase("ret")) {
            JavaAgent.consumeReturn(clazz, method, retVal);
        }
    }
}
