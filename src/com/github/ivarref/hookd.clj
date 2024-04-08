(ns com.github.ivarref.hookd
  (:import (com.github.ivarref.hookd JavaAgent)
           (java.util.function Consumer)))

(defn clear! [className]
  (JavaAgent/clear className))

(defn install! [f classes-and-methods]
  (doseq [[className methodName] classes-and-methods]
    (JavaAgent/addPrePost
      className
      methodName
      (reify Consumer
        (accept [_ java-map]
          (f (reduce-kv (fn [o k v]
                          (assoc o (keyword k)
                                   (cond (= "args" k)
                                         (into [] v)

                                         :else
                                         v)))
                        (sorted-map)
                        (into {} java-map))))))))
