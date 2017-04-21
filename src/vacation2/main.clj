(ns vacation2.main
  (:gen-class)
  (:require [clojure.string]
            [clojure.tools.cli]))

; LOGGING

(defn log [& args]
  "Log the given arguments. This does nothing, but is overwritten in parse-args
  if the debug option is enabled."
  nil)
(def logger (agent nil))
(defn log-debug [& args]
  "Log the given arguments. This function is used when debugging is enabled."
  (send logger (fn [_] (apply println args))))

; PARSE COMMAND LINE OPTIONS

(def cli-options
  [["-c" "--customers N" "Number of customers"
    :default 100
    :parse-fn #(Integer/parseInt %)]
   ["-r" "--reservations N" "Number of reservations"
    ; Corresponds to parameter -t in vacation.
    :default 1000
    :parse-fn #(Integer/parseInt %)]
   ["-n" "--relations N" "Number of flights/rooms/cars"
    ; Corresponds to parameter -r in vacation.
    :default 500
    :parse-fn #(Integer/parseInt %)]
   ;["-w" "--workers N" "Number of workers"
   ; :default 20
   ; :parse-fn #(Integer/parseInt %)]
   ["-d" "--debug" "Print debug information"]
   ["-h" "--help" "Print help information"]])

(def usage-info
  (str
"Usage: ./vacation2 [options]

Options:

" (:summary (clojure.tools.cli/parse-opts nil cli-options))))

(defn parse-args [args]
  "Parse command line arguments.

  Prints help or error messages as needed.

  Returns the options if everything went as expected, or nil if the program
  should not proceed. Hence, can be used in a when-let."
  (let [{:keys [options errors]} (clojure.tools.cli/parse-opts args cli-options)
        print-error? (not-empty errors)
        print-help?  (or (not-empty errors) (:help options))
        proceed?     (not (or print-error? print-help?))]
    (when print-error?
      (println "ERROR: Error when parsing command line arguments:\n "
        (clojure.string/join "\n  " errors) "\n"))
    (when print-help?
      (println usage-info))
    ; overwrite "log" function if debugging is enabled
    (when (:debug options)
      (def log log-debug))
    (if proceed?
      (do
        (log "options: " options)
        options)
      nil)))

; HELPER FUNCTIONS

(defn initialize-data [n-customers n-reservations n-relations]
  (letfn [(rand-seats []
            (* (+ (rand 5) 1) 100))
          (rand-price []
            (+ (* (rand 5) 10) 50))
          (generate-relation []
            (for [i (range n-relations)]
              (ref {:id i :seats (rand-seats) :price (rand-price)})))]
    {:reservations
      (for [i (range n-reservations)]
        (ref {:id i :fulfilled? false :total 0}))
     :cars    (generate-relation)
     :flights (generate-relation)
     :rooms   (generate-relation)}))

(defn zip [& lists]
  "Zip m lists of n elements into list of n m-tuples.

  > (zip [1 2 3 4] [5 6 7 8] [9 10 11 12])
  ([1 5 9] [2 6 10] [3 7 11] [4 8 12])

  Based on http://stackoverflow.com/a/2588385/8137"
  (apply map vector lists))

; BEHAVIORS

(def customer-behavior
  (behavior
    [customer-id]
    [reservation done?]
    (log "start reservation" (:id @reservation) "by customer" customer-id)
    ; TODO
    (deliver done? true)))

; MAIN

(defn -main [& args]
  "Main function. `args` should be a list of command line arguments."
  (when-let [options (parse-args args)]
    (let [{n-customers    :customers
           n-reservations :reservations
           n-relations    :relations} options
          {:keys [reservations cars flights rooms]}
            (initialize-data n-customers n-reservations n-relations)
          customer-actors
            (map #(spawn customer-behavior %) (range n-customers))
          done-promises
            (repeatedly n-reservations promise)]
      (when (not= (rem n-reservations n-customers) 0)
        (println "WARNING: number of reservations is not divisible by number"
          "of customers."))
      (doseq [[customer reservation done?] (zip (cycle customer-actors) reservations done-promises)]
        (send customer reservation done?))
      (doseq [done? done-promises]
        (deref done?))))
  (shutdown-agents))
