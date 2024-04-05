#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd $DIR

trap 'trap - SIGTERM && kill -- -$$;' SIGINT SIGTERM EXIT

# https://jvns.ca/blog/2020/06/28/entr/
while true
do
{ git ls-files; git ls-files . --exclude-standard --others; } | entr -dc ./test.sh
done
