package com.github.ivarref.hookd;

import java.util.ArrayList;
import java.util.function.BiFunction;

public class NativeOverride {

    public static long currentTimeMillis() {
        long v = System.currentTimeMillis();
        JavaAgent.TransformConfig jlSystem = JavaAgent.retMod.getOrDefault("NATIVE_java.lang.System", new JavaAgent.TransformConfig());
        BiFunction fn = jlSystem.modifiers.get("currentTimeMillis");
        if (fn != null) {
            Object retVal = fn.apply(new ArrayList<>(), v);
            return (long)retVal;
        } else {
            return v;
        }
    }

}
