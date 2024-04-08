package com.github.ivarref.hookd;

public class PreFunctionInvoke {

    public static Object enterPre(Object fromObj, String clazz, String method, String id, Long startTime, Object[] args) {
        Callback.enterPre(fromObj, clazz, method, id, startTime, args);
        return null;
    }
}
