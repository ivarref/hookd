#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd $DIR

set -e

./test.sh

VERSION="$(clojure -T:release ivarref.pom-patch/set-patch-version! :patch :commit-count)"

clojure -T:install

git add pom.xml README.md
git commit -m"Release $VERSION"
git tag -a v"$VERSION" -m "Release v$VERSION"
git push --follow-tags

clojure -T:deploy
echo "Released $VERSION"
