package com.github.ivarref.hookd;

public class PostFunctionInvoke {
    public static Object enterPost(Object fromObj, String clazz, String method, Long stopTime, Object[] args, Boolean isConstructor, Object retVal) {
        Callback.enterPost(fromObj, clazz, method, stopTime, args, isConstructor, retVal);
        return null;
    }
}
