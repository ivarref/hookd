package com.github.ivarref.hookd;

public class CallbackFunction {

    public static Object callback(String op, Object fromObj, String clazz, String method, Object[] args, Object retVal) {
        if (op.equalsIgnoreCase("pre")) {
            JavaAgent.consumePre(clazz, method, fromObj, args);
            return null;
        } else if (op.equalsIgnoreCase("ret")) {
            JavaAgent.consumeReturn(clazz, method, retVal);
            return null;
        } else if (op.equalsIgnoreCase("retMod")) {
            return JavaAgent.modifyReturn(clazz, method, retVal);
        } else {
            return null;
        }
    }
}
