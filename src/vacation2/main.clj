(ns vacation2.main
  (:gen-class)
  (:require [clojure.string]
            [clojure.tools.cli]
            [base64]
            [crypto.password.bcrypt :as password]
            [util :refer :all]))

; LOGGING

(defn log [& args]
  "Log the given arguments. This does nothing, but is overwritten in parse-args
  if the debug option is enabled."
  nil)

(def log-lock (Object.))

(defn log-debug [& args]
  "Log the given arguments. This function is used when debugging is enabled."
  (locking log-lock
    (apply println args)))

; PARSE COMMAND LINE OPTIONS

(def cli-options
  [["-v" "--version X" "Version (original, txact)"
    ; There are two versions of this program, "original" works similar to the
    ; original vacation benchmark, while "txact" uses secondary worker actors.
    :default "original"
    :validate [#(contains? #{"original" "txact"} %) "Must be original or txact"]]
   ["-w" "--workers N" "Number of workers"
    ; Workers are called clients in vacation, the option is -c there.
    :default 20
    :parse-fn #(Integer/parseInt %)]
   ["-s" "--secondary-workers N" "Number of secondary workers"
    ; New, not in vacation.
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
   ["-p" "--password-work-factor N" "Work factor for password generation"
    :default 11
    :parse-fn #(Integer/parseInt %)]
   ["-d" "--debug" "Print debug information"]
   ["-h" "--help" "Print help information"]])

(def usage-info
  (str
"Usage: ./vacation2 [options]

Options:

" (:summary (clojure.tools.cli/parse-opts nil cli-options))))

(defn parse-args [cli-args]
  "Parse command line arguments.

  Prints help or error messages as needed.

  Returns the options if everything went as expected, or nil if the program
  should not proceed. Hence, can be used in a when-let."
  (let [{args :options errors :errors}
          (clojure.tools.cli/parse-opts cli-args cli-options)
        print-error? (not-empty errors)
        print-help?  (or (not-empty errors) (:help args))
        proceed?     (not (or print-error? print-help?))]
    (when print-error?
      (println "ERROR: Error when parsing command line arguments:\n "
        (clojure.string/join "\n  " errors) "\n"))
    (when print-help?
      (println usage-info))
    ; overwrite "log" function if debugging is enabled
    (when (:debug args)
      (def log log-debug))
    (if proceed?
      (let [options {:version              (case (:version args)
                                             "txact" :txact
                                                     :original)
                     :n-workers            (:workers args)
                     :n-secondary-workers  (if (not= (:version args) "txact")
                                             0
                                             (:secondary-workers args))
                     :n-reservations       (:reservations args)
                     :n-relations          (:relations args)
                     :n-queries            (:queries args)
                     :password-work-factor (:password-work-factor args)}]
        (println "options: " options)
        (when (and (= (:version args) :original)
                   (not= (:secondary-workers args) 20)) ; 20 is the default
          (println "WARNING: did not expect number of secondary workers to be"
            "specified when version is 'original'."))
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

(defn generate-pnr [reservation password-work-factor]
  "Generate Passenger Name Record (PNR).
  The generated PNR is fake, but based on the information described on
  Wikipedia.

  https://en.wikipedia.org/wiki/Passenger_name_record"
  (let [name-of-passenger (:id @reservation)
        info-travel-agent "vacation-benchmark"
        ticket-number     (rand-int 10000)
        itinerary         (:destination @reservation)
        timestamp         (System/currentTimeMillis)
        password          (password/encrypt
                            (apply str (repeatedly 20
                              #(rand-nth "abcdefghijklmnopqrstuvwxyz")))
                            password-work-factor)
        data              (clojure.string/join "//"
                             [name-of-passenger
                              info-travel-agent
                              ticket-number
                              itinerary
                              timestamp
                              password])]
    (base64/str->base64 data)))

(defn process-reservation-original
  [reservation
   {:keys [cars flights rooms]}
   {:keys [n-queries password-work-factor]}]
  "Process a reservation: find two flights, a room, and a car for the
  reservation, generate a PNR, and update the data structures.

  This version is similar to the original vacation benchmark."
  (dosync
    (log "start tx for reservation" (:id @reservation))
    (let [n-people      (:n-people @reservation)
          found-flight1 (look-for-seats (random-subset n-queries flights) n-people)
          found-flight2 (look-for-seats (random-subset n-queries flights) n-people)
          found-room    (look-for-seats (random-subset n-queries rooms) n-people)
          found-car     (look-for-seats (random-subset n-queries cars) n-people)
          pnr           (generate-pnr reservation password-work-factor)]
      (log "reserving:"
        (if found-flight1 (str "flight1 " (:id @found-flight1)) "no flight") "-"
        (if found-flight2 (str "flight2 " (:id @found-flight2)) "no flight") "-"
        (if found-room    (str "room "   (:id @found-room))   "no room")     "-"
        (if found-car     (str "car "    (:id @found-car))    "no car"))
      (when found-flight1 (reserve-relation reservation found-flight1 n-people))
      (when found-flight2 (reserve-relation reservation found-flight2 n-people))
      (when found-room    (reserve-relation reservation found-room n-people))
      (when found-car     (reserve-relation reservation found-car n-people))
      (alter reservation assoc
        :status :processed
        :pnr    pnr)))
  (log "finished reservation" (:id @reservation)))

(defn process-reservation-txact
  [reservation
   {:keys [cars flights rooms]}
   {:keys [n-queries password-work-factor]}
   secondary-workers]
  "Process a reservation: find two flights, a room, and a car for the
  reservation, generate a PNR, and update the data structures.

  This version books the items in a separate actor."
  (dosync
    (log "start tx for reservation" (:id @reservation))
    (send (rand-nth secondary-workers) :flight flights reservation n-queries)
    (send (rand-nth secondary-workers) :flight flights reservation n-queries)
    (send (rand-nth secondary-workers) :room   rooms   reservation n-queries)
    (send (rand-nth secondary-workers) :car    cars    reservation n-queries)
    (let [pnr (generate-pnr reservation password-work-factor)]
      (alter reservation assoc
        :status :in-process
        :pnr    pnr)))
  (log "finished reservation" (:id @reservation)))

; BEHAVIORS

(def reserve-relation-behavior
  (behavior [secondary-worker-id master]
    [relation-name relation reservation n-queries]
    (do
      (log "reserve" relation-name "for reservation" (:id @reservation)
        "by secondary worker" secondary-worker-id)
      (dosync
        (let [n-people (:n-people @reservation)
              found-relation
                (look-for-seats (random-subset n-queries relation) n-people)]
          (when found-relation
            (log "reserving" relation-name (:id @found-relation))
            (reserve-relation reservation found-relation n-people))))
      (send master :done relation-name (:id @reservation)))))

; In vacation, this corresponds to a client.
(def reservation-behavior
  (behavior [worker-id master secondary-workers data options]
    ; Note: as opposed to the vacation benchmark, we only support making
    ; reservations, not deleting customers or updating tables.
    [_reserve reservation]
    ; Instead of matching with :reserve, we write _reserve, because the version
    ; of Clojure with delayed messages does not support pattern matching.
    (do
      (log "start reservation" (:id @reservation) "by worker" worker-id)
      (case (:version options)
        :txact
          (process-reservation-txact reservation data options secondary-workers)
        ;original
          (process-reservation-original reservation data options))
      (send master :done :reservation (:id @reservation)))))

(def done? (promise))

(def master-waiting-behavior
  (behavior [start-time n]
    [_done _key _id] ; _key is one of :reservation, :car, :flight, :room
    ; Instead of matching with :done, we write _done, because the version of
    ; Clojure with delayed messages does not support pattern matching.
    (if (= n 1)
      (do
        (println "Total execution time:"
          (format "%.3f" (/ (- (System/nanoTime) start-time) 1000000.0)) "ms")
        (log "done")
        #_(log-data data)
        (deliver done? true))
      (become :same start-time (dec n)))))

(def master-initial-behavior
  (behavior [{:keys [version n-workers n-secondary-workers n-reservations] :as options}]
    [_start]
    ; Instead of matching with :start, we write _start, because the version of
    ; Clojure with delayed messages does not support pattern matching.
    (let [{:keys [reservations cars flights rooms] :as data}
            (initialize-data options)
          ; It is important to wrap the two maps below in doall, so that they
          ; are executed here, where *actor* refers to the master actor, and not
          ; in another place. Stupid lazy Clojure.
          secondary-workers
            (doall (map #(spawn reserve-relation-behavior % *actor*)
                        (range n-secondary-workers)))
            ; n-secondary-workers = 0 if version == :original
          reservation-workers
            (doall (map #(spawn reservation-behavior
                          % *actor* secondary-workers data options)
                        (range n-workers)))
          expected-n-done-messages
            (case version
              :txact (* n-reservations 5) ; 1 from reservation, 4 from relations
                     n-reservations)
          start-time
            (System/nanoTime)]
      #_(log-data data)
      (doseq [[worker reservation] (zip (cycle reservation-workers) reservations)]
        (send worker :reserve reservation))
      (become master-waiting-behavior start-time expected-n-done-messages))))

; MAIN

(defn -main [& args]
  "Main function. `args` should be a list of command line arguments."
  (when-let [options (parse-args args)]
    (send (spawn master-initial-behavior options) :start)
    (deref done?))
  ; TODO: implement validation
  (log "exit")
  (System/exit 0))
