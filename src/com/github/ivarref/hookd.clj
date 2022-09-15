(ns com.github.ivarref.hookd
  (:import (com.github.ivarref.hookd JavaAgent)
           (java.util.function Consumer)))

(defn install-return-consumer! [className methodName f]
  (JavaAgent/addPostHook
    className
    methodName
    (reify Consumer
      (accept [_ x]
        (f x)))))
