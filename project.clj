(defproject hookd "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.javassist/javassist "3.30.2-GA"]]
  :java-source-paths ["java"]
  :javac-options ["-target" "1.6"]
  :repl-options {:init-ns agent.core})
