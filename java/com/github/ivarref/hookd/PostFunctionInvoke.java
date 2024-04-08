package com.github.ivarref.hookd;

public class PostFunctionInvoke {
    public static Object enterPost(String op, Object fromObj, String clazz, String method, String id, Long startTime, Long stopTime, Object[] args, Object retVal) {
        Callback.enterPost(fromObj, clazz, method, id, startTime, stopTime, args, retVal);
        return null;
    }
}
