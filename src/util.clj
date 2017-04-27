(ns util)

(defn zip [& lists]
  "Zip m `lists`, each a list of n elements, into list of n elements, each
  a vector of m elements.

  > (zip [1 2] [3 4] [5 6])
  ([1 3 5] [2 4 6])

  Based on http://stackoverflow.com/a/2588385/8137"
  (apply map vector lists))

(defn random-subset [n coll]
  "Get `n` randomly picked elements from `coll`.
  Sampling is done with replacement, i.e. elements may appear multiple times."
  (repeatedly n #(rand-nth coll)))
