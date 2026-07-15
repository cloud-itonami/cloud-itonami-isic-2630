(ns commsdevice.facts-test
  (:require [clojure.test :refer [deftest is]]
            [commsdevice.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "USA"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["JPN" "USA"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(deftest every-jurisdiction-requires-sar-and-safety-evidence
  (doseq [iso3 (keys facts/catalog)]
    (let [checklist (facts/evidence-checklist iso3)]
      (is (some #(re-find #"(?i)sar" %) checklist)
          (str iso3 " must require RF-exposure/SAR evidence -- a real, radio-transmitter-specific item"))
      (is (some #(re-find #"(?i)62368" %) checklist)
          (str iso3 " must still require the general IEC 62368-1 safety baseline")))))
