(ns base64-test
  (:require [clojure.test :refer :all]
            [base64 :refer :all]))

(def examples
  [{:in "t"    :out "dA=="}
   {:in "te"   :out "dGU="}
   {:in "tes"  :out "dGVz"}
   {:in "test" :out "dGVzdA=="}])

(deftest encode-test
  (doseq [{:keys [in out]} examples]
    (is (= (str->base64 in) out))))

(deftest decode-test
  (doseq [{:keys [in out]} examples]
    (is (= (base64->str out) in))))

(deftest reversible-test
  (doseq [{:keys [in]} examples]
    (is (= (base64->str (str->base64 in)) in))))
