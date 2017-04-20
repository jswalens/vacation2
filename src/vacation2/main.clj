(ns vacation2.main
  (:gen-class)
  (:require [clojure.string]
            [clojure.tools.cli]))

(defn log [& args]
  "Log the given arguments. This does nothing, but is overwritten in main if the
  debug option is enabled."
  nil)

(def logger (agent nil))
(defn log-debug [& args]
  "Log the given arguments. This function is used when debugging is enabled."
  (send logger (fn [_] (apply println args))))

(def cli-options
  [["-n" "--customers N" "Number of customers"
    :default 100
    :parse-fn #(Integer/parseInt %)]
   ["-r" "--reservations N" "Number of reservations"
    ; Corresponds to parameter -t in vacation.
    :default 1000
    :parse-fn #(Integer/parseInt %)]
   ;["-w" "--workers N" "Number of workers"
   ; :default 20
   ; :parse-fn #(Integer/parseInt %)]
   ["-d" "--debug" "Print debug information"]
   ["-h" "--help" "Print help information"]])

(def usage
  (str
"Usage: ./vacation2 [options]

Options:

" (:summary (clojure.tools.cli/parse-opts nil cli-options))))

(def customer-behavior
  (behavior
    []
    []
    (log "start reservation")
    (dosync
      (println "TODO"))))

(defn -main [& args]
  "Main function. `args` should be a list of command line arguments."
  (let [{:keys [options errors]} (clojure.tools.cli/parse-opts args cli-options)
        print-error? (not-empty errors)
        print-help?  (or (not-empty errors) (:help options))]
    (when print-error?
      (println "ERROR: Error when parsing command line arguments:\n "
        (clojure.string/join "\n  " errors) "\n"))
    (when print-help?
      (println usage))
    ; overwrite "log" function if debugging is enabled
    (when (:debug options)
      (def log log-debug))
    (when-not (or print-error? print-help?)
      (log "options: " options)
      (let [reservations-per-customer
              (quot (:reservations options) (:customers options))
            customers
              (for [i (range (:customers options))]
                (spawn customer-behavior))]
        (when (not= (rem (:reservations options) (:customers options)) 0)
          (println "WARNING: number of reservations is not divisible by number"
            "of customers. Using" reservations-per-customer "reservations per"
            "customer, so only" (* reservations-per-customer (count customers))
            "in total."))
        (doseq [c customers]
          (dotimes [_i reservations-per-customer]
            (send c))))))
  (shutdown-agents))
