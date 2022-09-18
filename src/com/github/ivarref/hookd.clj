(ns com.github.ivarref.hookd
  (:import (com.github.ivarref.hookd JavaAgent)
           (java.util.function BiConsumer Consumer)))

(defn clear! [className]
  (JavaAgent/clear className))

(defn install-return-consumer! [className methodName f]
  (assert (fn? f) "Expected f to be a function")
  (JavaAgent/addReturnConsumer
    className
    methodName
    (reify Consumer
      (accept [_ x]
        (f x)))))

(defn install-pre-hook! [className methodName f]
  (assert (fn? f) "Expected f to be a function")
  (JavaAgent/addPreHook
    className
    methodName
    (reify BiConsumer
      (accept [_ t x]
        (f t x)))))
