#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd $DIR

set -e

rm -rf target/ || true
lein jar
rm -rf tmp/ || true
rm hookd.jar || true
mkdir tmp

unzip -q target/hookd-0.1.0-SNAPSHOT.jar -d tmp/
jar cmf META-INF/MANIFEST.MF hookd.jar -C tmp .

VERSION="$(clojure -T:release ivarref.pom-patch/get-version)"
clojure -T:release ivarref.pom-patch/set-version! :version '"DEV"'
clojure -T:install
clojure -T:release ivarref.pom-patch/set-version! :version '"'"$VERSION"'"'

cd agentuser

env JAVA_TOOL_OPTIONS="-Djdk.attach.allowAttachSelf=true" lein test || true

cd $DIR
