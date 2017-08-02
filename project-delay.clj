(defproject vacation2 "0.1.0-SNAPSHOT"
  :description "Vacation2 benchmark, based on STAMP's vacation benchmark."
  :resource-paths ["resources/clojure-1.8.0-transactional-actors-delay-3.jar"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5" :exclusions [org.clojure/clojure]]
                 [crypto-password "0.2.0"]
                 ;[com.taoensso/timbre "4.1.4" :exclusions [org.clojure/clojure]]
                 ]
  :main ^:skip-aot vacation2.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})