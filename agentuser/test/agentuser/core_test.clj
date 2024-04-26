(ns agentuser.core-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is] :as test]
    [com.github.ivarref.hookd :as hookd])
  (:import (com.github.ivarref ConstructorThrows ExceptionIsThrown RecursionClass SomeClass)
           (com.github.ivarref.hookd JavaAgent)
           (java.lang.reflect Field)
           (java.net HttpCookie)))

(defonce lock (Object.))

(defn clean-and-lock [f]
  (locking lock
    (hookd/uninstall!)
    (f)))

(test/use-fixtures :each clean-and-lock)

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

(deftest uninstall-works
  (let [arg (atom nil)]
    (hookd/install-post!
      #(reset! arg %)
      [["com.github.ivarref.SomeClass" "returnInt"]])
    (hookd/uninstall!)
    (let [someInst (SomeClass.)]
      (is (= 3 (.returnInt someInst)))
      (is (= nil @arg)))))

(deftest wiretap-like
  (let [arg (atom nil)]
    (hookd/install-post!
      #(reset! arg %)
      [["com.github.ivarref.SomeClass" "returnInt"]])
    (let [someInst (SomeClass.)]
      (is (= 3 (.returnInt someInst)))
      (is (= 3 (:result @arg)))
      (is (int? (:spent-ns @arg))))))

(deftest recursion-test
  (let [st (atom [])
        retval (atom [])]
    (hookd/install!
      (fn [{:keys [pre? args result]}]
        (if pre?
          (swap! st conj (first args))
          (swap! retval conj result)))
      [["com.github.ivarref.RecursionClass" "recursion"]])
    (let [someInst (RecursionClass.)]
      (.recursion someInst 5)
      (is (= [0 1 2 3 4 5] @retval))
      (is (= [5 4 3 2 1 0] @st)))))

(deftest wiretap-throw-exception
  (let [ctx (atom nil)]
    (hookd/install-post!
      (fn [m]
        (reset! ctx m))
      [["com.github.ivarref.ExceptionIsThrown" "returnInt"]])
    (let [someInst (ExceptionIsThrown.)
          exception-is-re-thrown (atom false)]
      (try
        (.returnInt someInst)
        (catch Throwable t
          (reset! exception-is-re-thrown true)
          (is (some? t))))
      (is (true? @exception-is-re-thrown))
      (is (instance? Throwable (:error @ctx)))
      (is (true? (:error? @ctx))))))

(deftest constructor-test
  (let [ctx (promise)]
    (hookd/install-post!
      (fn [m]
        (deliver ctx m))
      [["com.github.ivarref.SomeClass" "SomeClass"]])
    (let [someInst (SomeClass.)]
      (is (some? @ctx))
      (is (= someInst (:this @ctx)))
      (is (= someInst (:result @ctx))))))

(deftest constructor-throws
  (let [ctx (promise)]
    (hookd/install-post!
      (fn [m]
        (deliver ctx m))
      [["com.github.ivarref.ConstructorThrows" "ConstructorThrows"]])
    (let [someInst (try
                     (ConstructorThrows. true)
                     (catch Exception _
                       nil))]
      (is (some? @ctx))
      (is (some? (:error @ctx)))
      (is (nil? someInst)))))

(deftest throw-on-unknown-class
  (try
    (hookd/install!
      (fn [_]
        nil)
      [["com.github.ivarref.MissingClass" "MissingMethod"]])
    (is false "Expected exception!")
    (catch RuntimeException rte
      (is (instance? RuntimeException rte)))))

(deftest throw-on-unknown-method
  (try
    (hookd/install!
      (fn [_]
        nil)
      [["com.github.ivarref.SomeClass" "MissingMethod"]])
    (is false "Expected exception!")
    (catch RuntimeException rte
      (is (instance? RuntimeException rte)))))
