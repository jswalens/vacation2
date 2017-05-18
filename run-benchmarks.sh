#!/bin/bash

set -ex

pwd="`pwd`"
rev=`git rev-parse HEAD | cut -c1-8`
date=`date "+%Y%m%dT%H%M"`
result_path="$pwd/results/$date-$rev"

lein=$pwd/lein

echo "Installing/checking lein..."
$lein version
echo "lein installed"

echo "Making uberjar"
$lein uberjar
echo "Uberjar made"

echo "Benchmarking..."

mkdir -p "$result_path"

for i in 1 2 3
do
    # ORIGINAL VERSION
    version="orig"
    for w in $(seq 2)
    do
        $lein run -- -v $version -w $w > "$result_path/$version-w$w-i$i.txt"
    done

    # TXACT VERSION
    version="txact"
    for w in $(seq 2)
    do
        for s in $(seq 2)
        do
            $lein run -- -v $version -w $w > "$result_path/$version-w$w-s$s-i$i.txt"
        done
    done
done

echo "Benchmark done"
