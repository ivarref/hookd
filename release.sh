#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd $DIR

set -e

rm -rf target/ || true
lein jar
rm -rf tmp/ || true
rm agent.jar || true
mkdir tmp

unzip -q target/hookd-0.1.0-SNAPSHOT.jar -d tmp/
jar cmf META-INF/MANIFEST.MF agent.jar -C tmp .

VERSION="$(clojure -X:release ivarref.pom-patch/set-patch-version! :patch :commit-count)"

#clojure -T:install

git add pom.xml README.md
git commit -m"Release $VERSION"
git tag -a v"$VERSION" -m "Release v$VERSION"
git push --follow-tags --force

clojure -X:deploy
echo "Released $VERSION"
