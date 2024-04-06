package com.github.ivarref.hookd;

public class PreFunctionInvoke {

    public static Object enterPre(String op, Object fromObj, String clazz, String method, String id, Long startTime, Long stopTime, Object[] args, Object retVal) {
        Callback.enterPre(fromObj, clazz, method, id, startTime, args);
        return null;
    }
}
