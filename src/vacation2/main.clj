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

; BEHAVIORS

(def customer-behavior
  (behavior
    []
    []
    (log "start reservation")
    (dosync
      (println "TODO"))))

; MAIN

(defn -main [& args]
  "Main function. `args` should be a list of command line arguments."
  (when-let [options (parse-args args)]
    (let [{:keys [reservations customers]} options
          reservations-per-customer (quot reservations customers)
          customer-actors
            (for [i (range customers)]
              (spawn customer-behavior))]
      (when (not= (rem reservations customers) 0)
        (println "WARNING: number of reservations is not divisible by number"
          "of customers. Using" reservations-per-customer "reservations per"
          "customer, so only" (* reservations-per-customer customers)
          "in total."))
      (doseq [c customer-actors]
        (dotimes [_i reservations-per-customer]
          (send c)))))
  (shutdown-agents))
