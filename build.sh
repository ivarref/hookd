#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd $DIR

set -e

rm -rf target/ || true
lein jar
rm -rf tmp/ || true
rm agent.jar || true
mkdir tmp

unzip -q target/agent-0.1.0-SNAPSHOT.jar -d tmp/
jar cmf META-INF/MANIFEST.MF agent.jar -C tmp .

clj -T:install
