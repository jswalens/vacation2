# vacation2

This is the vacation2 benchmark, an adapted version of the Vacation benchmark of the STAMP benchmark suite [Minh2008] ported to Clojure and extended with the use of transactional actors [Swalens2017].

## How to run

You can run the program using [Leiningen](https://leiningen.org/). If you don't have Leiningen, it is also included in the file `lein`, just substitute `lein` with `./lein` below.

Run the benchmark as follows (all parameters are optional):

    $ lein run -- -v txact -w 4 -s 8 -t 30 -n 300

To run the original version of the benchmark, which does not use transactional actors and is similar to the benchmark of the STAMP suite, execute:

    $ lein run -- -v orig -w 4 -t 30 -n 300

Parameters:
* `-v`: `orig` for the original version, `txact` for the version with transactional actors.
* `-w`: the number of primary worker actors.
* `-s`: the number of secondary worker actors.
* `-t`: the number of reservations.
* `-n`: the number of queries per relation per reservation.

(Run `lein run -- -h` to get this description and more.)

Running the program prints the given options and the total execution time to the screen.

## Running the benchmarks

There are three scripts, `run-benchmarks.sh`, `run-benchmarks-fast.sh`, and `run-benchmarks-slow.sh`, that run the benchmark for a variation of parameters. (slow runs fewer options, fast more.) The results are written to subdirectory of `results/`.

In `results/`, there are a few scripts to generate the graphs and table of the paper [Swalens2017]. The data set used to generate these is also included.

## Two versions of transactional actors

By default, `project.clj` refers to `resources/clojure-1.8.0-transactional-actors-3.jar`. This is a fork of Clojure 1.8.0, extended with support for transactional actors. Its source code can be found at https://github.com/jswalens/transactional-actors; some details on the implementation can be found in [Swalens2017].

Next to this, there's also `resources/clojure-1.8.0-transactional-actors-delay-3.jar`. This is a fork of Clojure 1.8.0, that supports transactional actors by delaying messages sent in a transaction until the transaction has committed.

It may be useful to compare the two implementations of transactional actors. To do so, either copy `project-dependency.clj` or `project-delay.clj` to `project.clj`.

## License
Licensed under the MIT license, included in the file `LICENSE`.

## References

[Swalens2017] J. Swalens, J. De Koster, and W. De Meuter. 2017. "Transactional Actors: Communication in Transactions". In _Proceedings of the 4th International Workshop on Software Engineering for Parallel Systems (SEPS'17)_.
[Minh2008] C. C. Minh, J. Chung, C. Kozyrakis, and K. Olukotun. 2008. "STAMP: Stanford Transactional Applications for Multi-Processing". In _Proceedings of the IEEE International Symposium on Workload Characterization (IISWC'08)_.
