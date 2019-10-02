#!/bin/bash
#
# Benchmarking script
#
# Usage:
# Use --all to run more extensive tests, or --quick to run fewer variations.
# Call this as `JVM_OPTS="..." ./benchmark.sh` to pass arguments to the JVM.
#
# To use Chocola (sending messages with dependencies), run:
# $ cp project-dependency.clj project.clj && ./benchmark.sh
# To use the fork of Clojure that sends messages with delays, run:
# $ cp project-delay.clj project.clj && ./benchmark.sh

set -ex

pwd="`pwd`"
rev=`git rev-parse HEAD | cut -c1-8`
clj=`grep ":resource-paths" project.clj | sed -n 's/.*"resources\/\(.*\)\.jar".*/\1/p'`
date=`date "+%Y%m%dT%H%M"`
result_path="$pwd/results/$date-$rev"

: ${PARAMETERS:="-t 1000 -p 5 -r 50 -n 10"}

if [ "$1" == "--all" ]; then
    benchmark_parameters="all"
    is=$(seq 1 5)
    ws="1 2 4 6 8 10 12 14 16 18 20 22 24 26 28 30 32 34 36 38 40 42 44 46 48 50 52 54 56 58 60 62 64"
    ss="1 2 4 6 8 10 12 14 16 18 20 22 24 26 28 30 32 34 36 38 40 42 44 46 48 50 52 54 56 58 60 62 64"
elif [ "$1" == "--quick" ]; then
    benchmark_parameters="quick"
    is=$(seq 1 3)
    ws="1 2 4 8 16 32 64"
    ss="1 2 4 8 16 32 64"
else
    benchmark_parameters="normal"
    is=$(seq 1 5)
    ws="1 2 4 6 8 10 12 14 16 18 20 22 24 26 28 30 32 34 36 38 40 42 44 46 48 50 52 54 56 58 60 62 64"
    ss="1 2 4 8 16 32 64"
fi

info="Parameters: $PARAMETERS
Benchmark parameters: $benchmark_parameters
Revision: $rev
Clojure version: $clj
Date: $date"
echo "$info"

echo "Installing/checking lein..."
./lein version
echo "lein installed"

echo "Making uberjar"
./lein uberjar
echo "Uberjar made"

echo "Benchmarking..."

mkdir -p "$result_path"
echo "$info" > "$result_path/info.txt"

for i in $is
do
    # ORIGINAL VERSION
    version="original"
    for w in $ws
    do
        ./lein run -- -v $version -w $w $PARAMETERS > "$result_path/$version-w$w-i$i.txt"
    done

    # TXACT VERSION
    version="txact"
    for w in $ws
    do
        for s in $ss
        do
            ./lein run -- -v $version -w $w -s $s $PARAMETERS > "$result_path/$version-w$w-s$s-i$i.txt"
        done
    done
done

echo "Benchmark done"
