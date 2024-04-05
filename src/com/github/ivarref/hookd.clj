(ns com.github.ivarref.hookd
  (:import (com.github.ivarref.hookd JavaAgent)
           (java.util.function BiConsumer BiFunction Consumer Function)))

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

(defn install-return-modifier! [className methodName f]
  (assert (fn? f) "Expected f to be a function")
  (JavaAgent/addReturnModifier
    className
    methodName
    (reify BiFunction
      (apply [_ args retval]
        (f (into [] args) retval)))))

(defn install-pre-hook! [className methodName f]
  (assert (fn? f) "Expected f to be a function")
  (JavaAgent/addPreHook
    className
    methodName
    (reify BiConsumer
      (accept [_ t args]
        (f t (into [] args))))))

(defn install! [f classes-and-methods]
  (doseq [[className methodName] classes-and-methods]
    (JavaAgent/addPrePost
      className
      methodName
      (reify Consumer
        (accept [_ java-map]
          (f java-map))))))
