(ns com.github.ivarref.hookd
  (:import (com.github.ivarref.hookd JavaAgent)
           (java.util.function BiConsumer Consumer)))

(defn install-return-consumer! [className methodName f]
  (JavaAgent/addPostHook
    className
    methodName
    (reify Consumer
      (accept [_ x]
        (f x)))))

(defn install-pre-hook! [className methodName f]
  (JavaAgent/addPreHook
    className
    methodName
    (reify BiConsumer
      (accept [_ t x]
        (f t x)))))
