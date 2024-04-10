(ns com.github.ivarref.hookd
  (:require [clojure.spec.alpha :as s])
  (:import (com.github.ivarref.hookd JavaAgent)
           (java.util.function Consumer)))

(s/def :hookd/pre? boolean?)
(s/def :hookd/post? boolean?)
(s/def :hookd/error? boolean?)
(s/def :hookd/error #(instance? Throwable %))
(s/def :hookd/start int?)
(s/def :hookd/stop int?)
(s/def :hookd/spent-ns int?)
(s/def :hookd/spent-ms int?)
(s/def :hookd/args vector?)
(s/def :hookd/id string?)

(s/def :hookd/ctx-map
  (s/and
    (s/keys
      :req-un [:hookd/pre?
               :hookd/post?
               :hookd/start
               :hookd/args
               :hookd/id
               :hookd/this])
    (s/keys
      :opt-un [:hookd/stop
               :hookd/error?
               :hookd/error
               :hookd/result
               :hookd/spent-ns
               :hookd/spent-ms])))

(defn produce-wiretap-ctx-map
  [java-str-map]
  {:post [(s/valid? :hookd/ctx-map %)]}
  (let [{:keys [start stop] :as m} (reduce-kv (fn [o k v]
                                                (assoc o (keyword k)
                                                         (cond (= "args" k)
                                                               (into [] v)

                                                               :else
                                                               v)))
                                              (sorted-map)
                                              (into {} java-str-map))]
    (cond-> m
            stop (assoc :spent-ns (- stop start))
            stop (assoc :spent-ms (long (/ (- stop start)
                                           1000000))))))

(comment
  (produce-wiretap-ctx-map {"pre?"  true
                            "post?" false
                            "start" 10
                            "args" []
                            "id" "janei"
                            "this" nil
                            "error" (Exception. "boom")
                            "stop" 20}))

(defn uninstall!
  ([]
   (JavaAgent/clearAll))
  ([className]
   (JavaAgent/clear className)))

(defn install! [f classes-and-methods]
  (doseq [[className methodName] classes-and-methods]
    (JavaAgent/addPrePost
      className
      methodName
      (reify Consumer
        (accept [_ java-map]
          (f (produce-wiretap-ctx-map java-map)))))))

(defn install-post!
  "Like `install!` but ensures that `f` is only called **post** invocation.

   The following contextual data is will **always** be present in the map passed
   to `f`:

   | Key         | Value                                                            |
   | ----------- | ---------------------------------------------------------------- |
   | `:id`       | Uniquely identifies the call. Same value for pre and post calls. |
   | `:args`     | The seq of args that value of `:function` will be applied to.    |
   | `:start`    | Nanoseconds since some fixed but arbitrary origin time.          |
   | `:post?`    | `true`                                                           |
   | `:stop`     | Nanoseconds since some fixed but arbitrary origin time.          |
   | `:spent-ns` | Number of nanoseconds elapsed.                                   |
   | `:spent-ms` | Number of milliseconds elapsed. Coerced to long.                 |
   | `:result`   | The result of applying the value of `:function` to `:args`.      |
   | `:error`    | Any exception caught during computation of the result.           |
   | `:error?`   | True if an exception was thrown.                                 |
   | `:this`     | The object `this`. Will be nil for static methods.               |"
  [f classes-and-methods]
  (install! #(when (:post? %) (f %)) classes-and-methods))

(defn install-pre! [f classes-and-methods]
  (install! #(when (:pre? %) (f %)) classes-and-methods))
