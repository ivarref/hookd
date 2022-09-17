(ns agentuser.core-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.hookd :as hookd])
  (:import (com.github.ivarref SomeClass)))

(deftest a-test
  (let [atm (atom nil)
        cnt (atom 0)
        int-value (atom nil)]
    (hookd/install-return-consumer!
      "com.github.ivarref.SomeClass"
      "::Constructor"
      (fn [obj]
        (swap! cnt inc)
        (reset! atm obj)))
    (hookd/install-return-consumer!
      "com.github.ivarref.SomeClass"
      "returnInt"
      (fn [an-int]
        (reset! int-value an-int)))
    (let [someInst (SomeClass.)]
      (is (= 3 (.returnInt someInst)))
      (is (= 3 @int-value))
      (is (= @atm someInst))
      (is (= 1 @cnt)))))
