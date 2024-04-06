package com.github.ivarref.hookd;

public class ExceptionFunctionInvoke {
    public static Object enterException(Object fromObj, String clazz, String method, Long stopTime, Object[] args, Throwable t) {
        Callback.enterException(fromObj, clazz, method, stopTime, args, t);
        return null;
    }
}
