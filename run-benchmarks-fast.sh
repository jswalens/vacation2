#!/bin/bash

set -ex

pwd="`pwd`"
rev=`git rev-parse HEAD | cut -c1-8`
date=`date "+%Y%m%dT%H%M"`
result_path="$pwd/results/$date-$rev"

: ${PARAMETERS:="-t 30 -n 300"}
echo "Parameters: $PARAMETERS"

echo "Installing/checking lein..."
./lein version
echo "lein installed"

echo "Making uberjar"
./lein uberjar
echo "Uberjar made"

echo "Benchmarking (fast)..."

mkdir -p "$result_path"

for i in 1 2 3
do
    # ORIGINAL VERSION
    version="orig"
    for w in 1 2 3 4 6 8 12 16 24 32 48 64
    do
        ./lein run -- -v $version -w $w $PARAMETERS > "$result_path/$version-w$w-i$i.txt"
    done

    # TXACT VERSION
    version="txact"
    for w in 1 2 3 4 6 8 12 16 24 32 48 64
    do
        for s in 1 2 3 4 6 8 12 16 32 64
        do
            ./lein run -- -v $version -w $w -s $s $PARAMETERS > "$result_path/$version-w$w-s$s-i$i.txt"
        done
    done
done

echo "Benchmark done"
