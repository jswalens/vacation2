# vacation2

This is the vacation2 benchmark, an adapted version of the Vacation benchmark of the STAMP benchmark suite [Minh2008], written in Clojure and extended with the use of transactional actors [Swalens2017].

## How to run

You can run the program using [Leiningen](https://leiningen.org/). If you don't have Leiningen, it is also included in the file `lein`, just substitute `lein` with `./lein` below.

Run the benchmark as follows (all parameters are optional):

    $ lein run -- -v txact -w 4 -s 8 -t 30 -n 300

To run the original version of the benchmark, which does not use transactional actors and is similar to the benchmark of the STAMP suite, execute:

    $ lein run -- -v original -w 4 -t 30 -n 300

Parameters:
* `-v`: `original` for the original variant (default), `txact` for the variant with transactional actors.
* `-w`: the number of primary worker actors.
* `-s`: the number of secondary worker actors (only for txact variant).
* `-t`: number of reservations.
* `-n`: number of queries per relation per reservation.
* `-r`: number of flights/rooms/cars.
* `-p`: work factor for password generation.
* `-d`: print debug information.

(Run `lein run -- -h` to get this description and more.)

Running the program prints the given options and the total execution time to the screen.

## Different variants

There are two variants on this benchmark, which you can select using the command line argument `-v`:

* original: an adapted version of the vacation benchmark, that does not use transactional actors.
* txact: a version that splits transactions up and sends messages to secondary worker actors, using transactional actors.

Furthermore, there are two implementations of transactional actors included in this repository, in the folder `resources`:

* `chocola-X.Y.Z-standalone.jar` is the Chocola library that implements transactional actors as explained in [Swalens2017] and [Swalens2018]. Its source code can be found at https://github.com/jswalens/chocolalib. When a message is sent in a transaction, a dependency is attached. The message is processed immediately but may cause a roll back.
* `clojure-1.8.0-transactional-actors-delay-3.jar` is a fork of Clojure 1.8.0 that implements transactional actors by delaying messages sent in a transaction until the transaction has committed. (Similar to messages sent to agents in traditional Clojure.)

It may be useful to compare the two implementations of transactional actors. You can use Chocola by copying `project-dependency.clj` to `project.clj` (default) or the delayed version by copying `project-delay.clj` to `project.clj`.

## Differences between vacation from STAMP and our "vacation2"

This benchmark has several major differences with the original one from STAMP, including:
* Our benchmark implements different workers as actors instead of threads.
* We omitted some functionality: in the original customers can be deleted and items can be changed; we did not port this functionality.
* Each reservation consists of two flights (outbound and return) instead of one. This makes the examples in our paper more realistic.
* We always book the cheapest flight, room, and car, while the original benchmark books the most expensive one, strangely. Again, this makes the example in the paper easier to follow.
* We generate Passenger Name Records that contain a "password" for each reservation. This increases the computation time needed to process each reservation.

## Running the benchmarks

The script `benchmark.sh` runs the benchmark for a variation of parameters. Pass the option `--all` or `--quick` to run more/fewer variations. The results are written to subdirectory of `results/`.

In `results/`, there are a few scripts to generate the graphs and table of the paper [Swalens2017]. The data set used to generate these is also included.

## License

Licensed under the MIT license, included in the file `LICENSE`.

## References

[Swalens2017]
J. Swalens, J. De Koster, and W. De Meuter. 2017. "Transactional Actors: Communication in Transactions". In _Proceedings of the 4th ACM SIGPLAN International Workshop on Software Engineering for Parallel Systems (SEPS'17)_.

[Swalens2018]
J. Swalens, J. De Koster, and W. De Meuter. 2018. "Chocola: integrating futures, actors, and transactions". In _Proceedings of the 8th ACM SIGPLAN International Workshop on Programming Based on Actors, Agents, and Decentralized Control (AGERE'18)_.

[Minh2008]
C. C. Minh, J. Chung, C. Kozyrakis, and K. Olukotun. 2008. "STAMP: Stanford Transactional Applications for Multi-Processing". In _Proceedings of the IEEE International Symposium on Workload Characterization (IISWC'08)_.
