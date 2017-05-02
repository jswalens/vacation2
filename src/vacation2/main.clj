(ns vacation2.main
  (:gen-class)
  (:import java.util.Base64)
  (:require [clojure.string]
            [clojure.tools.cli]
            [util :refer :all]))

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
   ["-n" "--queries N" "Number of queries per relation per reservation"
    ; In vacation, this is the total number of queries per reservation; here,
    ; this is the number of queries per relation per reservation, so / 3.
    :default 100
    :parse-fn #(Integer/parseInt %)]
   ["-r" "--relations N" "Number of flights/rooms/cars"
    :default 500
    :parse-fn #(Integer/parseInt %)]
   ["-t" "--reservations N" "Number of reservations"
    ; Reservations are called "transactions" in vacation.
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
      (let [options {:n-customers    (:customers options)
                     :n-reservations (:reservations options)
                     :n-relations    (:relations options)
                     :n-queries      (:queries options)}]
        (log "options: " options)
        (when (not= (rem (:n-reservations options) (:n-customers options)) 0)
          (println "WARNING: number of reservations is not divisible by number"
            "of customers."))
        options)
      nil)))

; HELPER FUNCTIONS

(defn initialize-data [{:keys [n-reservations n-relations]}]
  (letfn [(rand-seats []
            (* (+ (rand-int 5) 1) 100))
          (rand-price []
            (+ (* (rand-int 5) 10) 50))
          (generate-relation []
            (for [i (range n-relations)]
              (ref {:id i :seats (rand-seats) :price (rand-price)})))]
    {:reservations
      (for [i (range n-reservations)]
        (ref {:id          i
              :status      :unprocessed ; :unprocessed|:in-process|:processed
              :destination (str (rand-int 100)) ; 1 of 100 locations
              ; Note: the concept of destination does not exist in the original
              ; vacation benchmark. We introduce it, to generate PNRs.
              :pnr         nil
              :total       0}))
     :cars    (generate-relation)
     :flights (generate-relation)
     :rooms   (generate-relation)}))

(defn- look-for-seats [relations n-seats]
  "Look for a relation with `n-seats` free seats in `relations`, and return the
  one with the minimal price. Return nil if no such relation can be found."
  ; Vacation searches for the maximal price, we return the cheapest option. This
  ; makes more sense to me: you want the reserve the cheapest car/flight/room.
  (dosync
    (->> relations
      (filter #(>= (:seats (deref %)) n-seats))
      (sort-by (comp :price deref))
      (first))))

(defn reserve-relation [reservation relation n-seats]
  "Reserve `n-seats` on `relation`, for `reservation`.

  Decreases the number of seats in `relation`, and updates the total in
  `reservation`."
  (dosync
    (alter relation update :seats - n-seats)
    (alter reservation update :total + (:price @relation))))

(defn- str->base64 [data]
  (.encodeToString (Base64/getEncoder) (.getBytes data)))

(defn- base64->str [data]
  (String. (.decode (Base64/getDecoder) data)))

(defn generate-pnr [reservation]
  "Generate Passenger Name Record (PNR).
  The generated PNR is fake, but based on the information described on
  Wikipedia.

  https://en.wikipedia.org/wiki/Passenger_name_record"
  (let [name-of-passenger (:id @reservation)
        info-travel-agent "vacation-benchmark"
        ticket-number     (rand-int 10000)
        itinerary         (:destination @reservation)
        timestamp         (System/currentTimeMillis)
        data              (clojure.string/join "//"
                             [name-of-passenger
                              info-travel-agent
                              ticket-number
                              itinerary
                              timestamp])]
    (str->base64 data)))

(defn process-reservation
  [reservation
   {:keys [cars flights rooms]}
   {:keys [n-queries]}]
  "Process a reservation: find a car, flight, and room for the reservation and
  update the data structures."
  ; TODO: does vacation always only reserve 1 seat?
  (dosync
    (let [found-car    (look-for-seats (random-subset n-queries cars) 1)
          found-flight (look-for-seats (random-subset n-queries flights) 1)
          found-room   (look-for-seats (random-subset n-queries rooms) 1)
          pnr          (generate-pnr reservation)]
      (when found-car    (reserve-relation reservation found-car 1))
      (when found-flight (reserve-relation reservation found-flight 1))
      (when found-room   (reserve-relation reservation found-room 1))
      (alter reservation assoc
        :status true
        :pnr    pnr))))
; TODO: add some logging

; BEHAVIORS

; TODO: this is not really a customer, but more a manager.
; TODO: what is a customer then? what is the difference between a customer and
; a reservation?
(def customer-behavior
  (behavior
    [customer-id data options]
    [reservation done?]
    (log "start reservation" (:id @reservation) "by customer" customer-id)
    (process-reservation reservation data options)
    (deliver done? true)))

; MAIN

(defn- log-data [{:keys [reservations cars flights rooms]}]
  "Write `data` to log."
  (log "reservations:" reservations)
  (log "cars:" cars)
  (log "flights:" flights)
  (log "rooms:" rooms))

(defn -main [& args]
  "Main function. `args` should be a list of command line arguments."
  (when-let [options (parse-args args)]
    (let [{:keys [n-customers n-reservations]} options
          {:keys [reservations cars flights rooms] :as data}
            (initialize-data options)
          customer-actors
            (map #(spawn customer-behavior % data options) (range n-customers))
          done-promises
            (repeatedly n-reservations promise)]
      (log-data data)
      (doseq [[customer reservation done?]
                (zip (cycle customer-actors) reservations done-promises)]
        (send customer reservation done?))
      (doseq [done? done-promises]
        (deref done?))
      (log-data data)))
  (Thread/sleep 1000) ; XXX
  (shutdown-agents))
