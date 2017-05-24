#!/bin/bash

set -ex

pwd="`pwd`"
rev=`git rev-parse HEAD | cut -c1-8`
date=`date "+%Y%m%dT%H%M"`
result_path="$pwd/results/$date-$rev"

echo "Installing/checking lein..."
./lein version
echo "lein installed"

echo "Making uberjar"
./lein uberjar
echo "Uberjar made"

echo "Benchmarking..."

mkdir -p "$result_path"

for i in 1 2 3
do
    # ORIGINAL VERSION
    version="orig"
    for w in 1 2 3 4 5 6 7 8 10 12 14 16 20 24 28 32 48 64
    do
        ./lein run -- -v $version -w $w > "$result_path/$version-w$w-i$i.txt"
    done

    # TXACT VERSION
    version="txact"
    for w in 1 2 3 4 5 6 7 8 10 12 14 16 20 24 28 32 48 64
    do
        for s in 1 2 3 4 5 6 7 8 10 12 14 16 32 64
        do
            ./lein run -- -v $version -w $w -s $s > "$result_path/$version-w$w-s$s-i$i.txt"
        done
    done
done

echo "Benchmark done"
