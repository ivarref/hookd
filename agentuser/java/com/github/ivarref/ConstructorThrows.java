package com.github.ivarref;

public class ConstructorThrows {
    public ConstructorThrows(boolean throwing) throws Exception {
        if (throwing) {
            throw new Exception("boom");
        }
    }
}
