(ns agentuser.core-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.hookd :as hookd])
  (:import (com.github.ivarref SomeClass)))

(defonce lock (Object.))

(deftest system-test
  (locking lock
    (let [getenv-args (atom nil)
          retval (atom :empty)]
      (hookd/install-pre-hook!
        "java.lang.System"
        "getenv"
        (fn [_ args]
          (reset! getenv-args (into [] args))))
      (hookd/install-return-consumer!
        "java.lang.System"
        "getenv"
        (fn [arg]
          (reset! retval arg)))
      (System/getenv "JANEI")
      (is (= ["JANEI"] @getenv-args))
      (is (= nil @retval)))))

(deftest system-retmod-test
  (locking lock
    (let []
      (hookd/install-return-modifier!
        "java.lang.System"
        "getenv"
        (fn [_]
          "RETVAL"))
      (is (= "RETVAL" (System/getenv "JANEI"))))))

(deftest a-test
  (locking lock
    (hookd/clear! "com.github.ivarref.SomeClass")
    (let [atm (atom nil)
          constructor-count (atom 0)
          int-value (atom nil)
          pre-count (atom 0)
          ret-count (atom 0)]
      (hookd/install-return-consumer!
        "com.github.ivarref.SomeClass"
        "::Constructor"
        (fn [obj]
          (swap! constructor-count inc)
          (reset! atm obj)))
      (hookd/install-return-consumer!
        "com.github.ivarref.SomeClass"
        "returnInt"
        (fn [an-int]
          (swap! ret-count inc)
          (reset! int-value an-int)))
      (hookd/install-pre-hook!
        "com.github.ivarref.SomeClass"
        "returnInt"
        (fn [_ _]
            (swap! pre-count inc)))
      (let [someInst (SomeClass.)]
        (is (= 0 @pre-count))
        (is (= 3 (.returnInt someInst)))
        (is (= 1 @pre-count))
        (is (= 3 @int-value))
        (is (= @atm someInst))
        (is (= 1 @constructor-count))
        (is (= 1 @ret-count))
        (.returnInt someInst)
        (is (= 2 @ret-count))
        (hookd/clear! "com.github.ivarref.SomeClass")
        (.returnInt someInst)
        (is (= 2 @ret-count))))))
