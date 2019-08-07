(defproject vacation2 "2.0.0"
  :description "Vacation2 benchmark, based on STAMP's vacation benchmark."
  :url "http://soft.vub.ac.be/~jswalens/chocola/"
  :resource-paths ["resources/chocola-2.0.0-standalone.jar"]
  :injections [(require 'chocola.core)]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5" :exclusions [org.clojure/clojure]]
                 [crypto-password "0.2.0"]
                 ;[com.taoensso/timbre "4.1.4" :exclusions [org.clojure/clojure]]
                 ]
  :main ^:skip-aot vacation2.main
  :profiles {:uberjar {:aot :all}})
