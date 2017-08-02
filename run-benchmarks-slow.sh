#!/bin/bash

set -ex

pwd="`pwd`"
rev=`git rev-parse HEAD | cut -c1-8`
clj=`grep ":resource-paths" project.clj | sed -n 's/.*"resources\/\(.*\)\.jar".*/\1/p'`
date=`date "+%Y%m%dT%H%M"`
result_path="$pwd/results/$date-$rev"

: ${PARAMETERS:="-t 30 -n 300"}

info="Parameters: $PARAMETERS
Revision: $rev
Clojure version: $clj
Date: $date"
echo $info

echo "Installing/checking lein..."
./lein version
echo "lein installed"

echo "Making uberjar"
./lein uberjar
echo "Uberjar made"

echo "Benchmarking..."

mkdir -p "$result_path"
echo $info > "$result_path/info.txt"

for i in 1 2 3 4 5
do
    # ORIGINAL VERSION
    version="orig"
    for w in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 18 20 22 24 26 28 30 32 36 40 44 48 52 56 60 64
    do
        ./lein run -- -v $version -w $w $PARAMETERS > "$result_path/$version-w$w-i$i.txt"
    done

    # TXACT VERSION
    version="txact"
    for w in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 18 20 22 24 26 28 30 32 36 40 44 48 52 56 60 64
    do
        for s in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 18 20 22 24 26 28 30 32 36 40 44 48 52 56 60 64
        do
            ./lein run -- -v $version -w $w -s $s $PARAMETERS > "$result_path/$version-w$w-s$s-i$i.txt"
        done
    done
done

echo "Benchmark done"
