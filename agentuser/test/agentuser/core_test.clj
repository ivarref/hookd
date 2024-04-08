(ns agentuser.core-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is]]
    [com.github.ivarref.hookd :as hookd])
  (:import (com.github.ivarref ExceptionIsThrown RecursionClass SomeClass)
           (com.github.ivarref.hookd JavaAgent)
           (java.lang.reflect Field)
           (java.net HttpCookie)))

(defonce lock (Object.))

#_(deftest system-test
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

#_(deftest system-retmod-test
    (locking lock
      (let []
        (hookd/install-return-modifier!
          "java.lang.System"
          "getenv"
          (fn [args retval]
            [args retval "new-value"]))
        (is (= [["JANEI"] nil "new-value"]
               (System/getenv "JANEI"))))))

#_(deftest system-retmod-test-2
    (locking lock
      (let []
        (hookd/install-return-modifier!
          "java.lang.System"
          "getenv"
          (fn [args retval]
            [args retval "new-value"]))
        (is (= [["JANEI"] nil "new-value"]
               (System/getenv "JANEI"))))))

(defn currTimeMillis []
  (System/currentTimeMillis))

#_(deftest currentTimeMillis-test
    (locking lock
      (hookd/install-return-modifier!
        "java.lang.System"
        "currentTimeMillis"
        (fn [org-args org-retval]
          123))
      (is (true? (contains? (into #{} JavaAgent/okTransform) "java.net.HttpCookie")))
      (let [cookie (HttpCookie. "janei" "janei")
            ^Field f (.getDeclaredField HttpCookie "whenCreated")]
        (.setAccessible f true)
        (is (= 123 (.get f ^Object cookie))))
      (is (= 123 (currTimeMillis)))))

#_(deftest a-test
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


#_(deftest wiretap-like
    (locking lock
      (hookd/uninstall! "com.github.ivarref.SomeClass")
      (let [arg (atom nil)]
        (hookd/install-post!
          #(reset! arg %)
          [["com.github.ivarref.SomeClass" "returnInt"]])
        (let [someInst (SomeClass.)]
          (is (= 3 (.returnInt someInst)))
          (is (= 3 (:result @arg)))
          (hookd/uninstall! "com.github.ivarref.SomeClass")))))


(deftest recursion-test
  (locking lock
    (hookd/uninstall! "com.github.ivarref.RecursionClass")
    (let [st (atom [])]
      (hookd/install-pre!
        (fn [{:keys [args]}]
          (swap! st conj (first args)))
        [["com.github.ivarref.RecursionClass" "recursion"]])
      (let [someInst (RecursionClass.)]
        (.recursion someInst 5)
        (is (= [5 4 3 2 1 0] @st))))))

#_(deftest wiretap-throw-exception
    (locking lock
      (hookd/uninstall! "com.github.ivarref.ExceptionIsThrown")
      (let [maps (atom [])]
        (hookd/install!
          (fn [m]
            (swap! maps conj m))
          [["com.github.ivarref.ExceptionIsThrown" "returnInt"]])
        (let [someInst (ExceptionIsThrown.)]
          (try
            (.returnInt someInst)
            (catch Throwable t
              (is (some? t))))
          (is (= 2 (count @maps)))
          (is (true? (:error? (second @maps))))
          (hookd/uninstall! "com.github.ivarref.ExceptionIsThrown")))))
