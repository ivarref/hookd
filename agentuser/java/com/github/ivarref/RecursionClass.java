package com.github.ivarref;

public class RecursionClass {
    public int recursion(int n) {
        if (n == 0) {
            return 0;
        } else {
            return 1 + recursion(n-1);
        }
    }
}
