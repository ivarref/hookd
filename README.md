# hookd

Consume return values from arbitrary Java methods in Clojure.

## Installation

[![Clojars Project](https://img.shields.io/clojars/v/com.github.ivarref/hookd.svg)](https://clojars.org/com.github.ivarref/hookd)

## Example usage

Make sure your JVM is started with `-Djdk.attach.allowAttachSelf=true`.

```clojure
(require '[com.github.ivarref.hookd :as hookd])

; I want to get a copy of the Apache Tomcat JDBC pool:
(defonce conn-pool (atom nil))

(hookd/install-return-consumer!
  "org.apache.tomcat.jdbc.pool.ConnectionPool"
  "::Constructor" ; Special method name to hook into the constructor(s)
  (partial reset! conn-pool))

; I want to peek at connections returned from the connection pool:
(hookd/install-return-consumer!
  "org.apache.tomcat.jdbc.pool.ConnectionPool"
  "getConnection"
  (fn [conn] ...))
```

## How it works

`hookd` uses [java agents](https://www.baeldung.com/java-instrumentation)
to modify the bytecode of class files. It attaches itself to the running
JVM. Repeated calls for the same class and method combination
will not modify the bytecode, only replace the consumer function.

## License

Copyright © 2022 Ivar Refsdal

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
