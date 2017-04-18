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
  [["-d" "--debug" "Print debug information"]
   ["-h" "--help" "Print help information"]])

(def usage
  (str
"Usage: ./vacation2 [options]

Options:

" (:summary (clojure.tools.cli/parse-opts nil cli-options))))

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
      (println "TODO")))
  (shutdown-agents))
