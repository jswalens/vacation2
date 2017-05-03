(ns vacation2.main
  (:gen-class)
  (:require [clojure.string]
            [clojure.tools.cli]
            [base64]
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
  [["-w" "--workers N" "Number of workers"
    ; Workers are called clients in vacation, the option is -c there.
    :default 20
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
      (let [options {:n-workers      (:workers options)
                     :n-reservations (:reservations options)
                     :n-relations    (:relations options)
                     :n-queries      (:queries options)}]
        (log "options: " options)
        (when (not= (rem (:n-reservations options) (:n-workers options)) 0)
          (println "WARNING: number of reservations is not divisible by number"
            "of workers."))
        options)
      nil)))

; HELPER FUNCTIONS

(defn initialize-data [{:keys [n-reservations n-relations]}]
  (letfn [(rand-number []
            (+ (rand-int 5) 1))
          (rand-seats []
            (* (+ (rand-int 5) 1) 100))
          (rand-price []
            (+ (* (rand-int 5) 10) 50))
          (generate-relation []
            (for [i (range n-relations)]
              (ref {:id i :seats (rand-seats) :price (rand-price)})))]
    {:reservations
      (for [i (range n-reservations)]
        (ref {:id          i
              :status      :unprocessed
              ; Status of reservation: :unprocessed|:processed
              :n-people    (rand-number)
              ; Number of people to reserve for
              ; Note: the original vacation benchmark only seems to reserve for
              ; one person per reservation.
              :destination (str (rand-int 100))
              ; Destination, 1 of 100 locations.
              ; Note: the concept of destination does not exist in the original
              ; vacation benchmark. We introduce it, to generate PNRs.
              :pnr         nil
              ; Passenger Name Record: stores some data about the reservation
              ; that can be printed on the passenger's ticket (maybe as a QR
              ; code)
              :total       0}))
              ; Total price
     :cars    (generate-relation)
     :flights (generate-relation)
     :rooms   (generate-relation)}))

(defn- log-data [{:keys [reservations cars flights rooms]}]
  "Write `data` to log."
  (log "reservations:" reservations)
  (log "cars:" cars)
  (log "flights:" flights)
  (log "rooms:" rooms))

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
    (base64/str->base64 data)))

(defn process-reservation
  [reservation
   {:keys [cars flights rooms]}
   {:keys [n-queries]}]
  "Process a reservation: find a car, flight, and room for the reservation and
  update the data structures."
  (dosync
    (log "start tx for reservation" (:id @reservation))
    (let [n-people     (:n-people @reservation)
          found-car    (look-for-seats (random-subset n-queries cars) n-people)
          found-flight (look-for-seats (random-subset n-queries flights) n-people)
          found-room   (look-for-seats (random-subset n-queries rooms) n-people)
          pnr          (generate-pnr reservation)]
      (log "reserving:"
        (if found-car    (str "car "    (:id @found-car))    "no car")    "-"
        (if found-flight (str "flight " (:id @found-flight)) "no flight") "-"
        (if found-room   (str "room "   (:id @found-room))   "no room"))
      (when found-car    (reserve-relation reservation found-car n-people))
      (when found-flight (reserve-relation reservation found-flight n-people))
      (when found-room   (reserve-relation reservation found-room n-people))
      (alter reservation assoc
        :status :processed
        :pnr    pnr)))
  (log "finished reservation" (:id @reservation)))

; BEHAVIORS

; In vacation, this corresponds to a client.
(def worker-behavior
  (behavior
    [worker-id master data options]
    [reservation]
    ; Note: as opposed to the vacation benchmark, we only support making
    ; reservations, not deleting customers or updating tables.
    (log "start reservation" (:id @reservation) "by worker" worker-id)
    (process-reservation reservation data options)
    (send master :done (:id @reservation))))

(def done? (promise))

(def master-waiting-behavior
  (behavior
    [n]
    [_done _reservation-id]
    (if (= n 1)
      (do
        (log "done")
        #_(log-data data)
        (deliver done? true))
      (become :same (dec n)))))

(def master-initial-behavior
  (behavior
    [{:keys [n-workers n-reservations] :as options}]
    [_start]
    (let [{:keys [reservations cars flights rooms] :as data}
            (initialize-data options)
          worker-actors
            (map #(spawn worker-behavior % *actor* data options)
              (range n-workers))]
      #_(log-data data)
      (doseq [[worker reservation] (zip (cycle worker-actors) reservations)]
        (send worker reservation))
      (become master-waiting-behavior n-reservations))))

; MAIN

(defn -main [& args]
  "Main function. `args` should be a list of command line arguments."
  (when-let [options (parse-args args)]
    (send (spawn master-initial-behavior options) :start)
    (deref done?))
  (log "exit")
  (System/exit 0))
