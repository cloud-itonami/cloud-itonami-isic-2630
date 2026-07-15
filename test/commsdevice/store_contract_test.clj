(ns commsdevice.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [commsdevice.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Amanogawa Handset AH-12 (JPN lot)" (:device-unit-name (store/device-unit s "device-1"))))
      (is (= "JPN" (:jurisdiction (store/device-unit s "device-1"))))
      (is (= 0.3 (:rf-power-deviation-actual (store/device-unit s "device-1"))))
      (is (= -1.5 (:rf-power-deviation-min (store/device-unit s "device-1"))))
      (is (= 1.5 (:rf-power-deviation-max (store/device-unit s "device-1"))))
      (is (false? (:eol-defect-unresolved? (store/device-unit s "device-1"))))
      (is (= 3.5 (:rf-power-deviation-actual (store/device-unit s "device-3"))))
      (is (true? (:eol-defect-unresolved? (store/device-unit s "device-4"))))
      (is (false? (:robotics-sim-verified? (store/device-unit s "device-1"))) "no robotics mission has run yet")
      (is (true? (:robotics-sim-verified? (store/device-unit s "device-5"))) "seeded as already-on-file")
      (is (= 45.0 (:bonding-press-platen-mass-kg (store/device-unit s "device-5"))))
      (is (> (:sim-peak-bonding-pressure-mpa (store/device-unit s "device-5")) 0.55)
          "device-5's real physics-2d-simulated bonding pressure exceeds the max acceptance bound")
      (is (< (:sim-peak-bonding-pressure-mpa (store/device-unit s "device-1")) 0.55)
          "device-1's real physics-2d-simulated bonding pressure clears the max acceptance bound")
      (is (> (:sim-peak-bonding-pressure-mpa (store/device-unit s "device-1")) 0.15)
          "device-1's real physics-2d-simulated bonding pressure clears the min acceptance bound")
      (is (false? (:device-unit-shipped? (store/device-unit s "device-1"))))
      (is (false? (:radio-conformity-certified? (store/device-unit s "device-1"))))
      (is (= ["device-1" "device-2" "device-3" "device-4" "device-5"]
             (mapv :id (store/all-device-units s))))
      (is (nil? (store/eol-screen-of s "device-1")))
      (is (nil? (store/requirements-verification-of s "device-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/shipment-history s)))
      (is (= [] (store/certificate-history s)))
      (is (zero? (store/next-shipment-sequence s "JPN")))
      (is (zero? (store/next-certificate-sequence s "JPN")))
      (is (false? (store/device-unit-already-shipped? s "device-1")))
      (is (false? (store/device-unit-already-certified? s "device-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :device-unit/upsert
                                 :value {:id "device-1" :device-unit-name "Amanogawa Handset AH-12 (JPN lot)"}})
        (is (= "Amanogawa Handset AH-12 (JPN lot)" (:device-unit-name (store/device-unit s "device-1"))))
        (is (= 0.3 (:rf-power-deviation-actual (store/device-unit s "device-1"))) "unrelated field preserved"))
      (testing "robotics-sim result commits via :device-unit/upsert and reads back"
        (store/commit-record! s {:effect :device-unit/upsert
                                 :value {:id "device-1" :robotics-sim-verified? true
                                        :robotics-sim-record {:mission-id "m-1" :passed? true}}})
        (is (true? (:robotics-sim-verified? (store/device-unit s "device-1"))))
        (is (= {:mission-id "m-1" :passed? true} (:robotics-sim-record (store/device-unit s "device-1"))))
        (is (= 0.3 (:rf-power-deviation-actual (store/device-unit s "device-1"))) "unrelated field still preserved"))
      (testing "verification / eol-screen payloads commit and read back"
        (store/commit-record! s {:effect :verification/set :path ["device-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/requirements-verification-of s "device-1")))
        (store/commit-record! s {:effect :eol-screen/set :path ["device-1"]
                                 :payload {:device-unit-id "device-1" :verdict :resolved}})
        (is (= {:device-unit-id "device-1" :verdict :resolved} (store/eol-screen-of s "device-1"))))
      (testing "device-unit shipment drafts a record and advances the sequence"
        (store/commit-record! s {:effect :device-unit/mark-shipped :path ["device-1"]})
        (is (= "JPN-SHP-000000" (get (first (store/shipment-history s)) "record_id")))
        (is (= "device-unit-shipment-draft" (get (first (store/shipment-history s)) "kind")))
        (is (true? (:device-unit-shipped? (store/device-unit s "device-1"))))
        (is (= 1 (count (store/shipment-history s))))
        (is (= 1 (store/next-shipment-sequence s "JPN")))
        (is (true? (store/device-unit-already-shipped? s "device-1")))
        (is (false? (store/device-unit-already-shipped? s "device-2"))))
      (testing "radio-conformity certificate drafts a record and advances the sequence"
        (store/commit-record! s {:effect :device-unit/mark-certified :path ["device-1"]})
        (is (= "JPN-RCC-000000" (get (first (store/certificate-history s)) "record_id")))
        (is (= "radio-conformity-certificate-draft" (get (first (store/certificate-history s)) "kind")))
        (is (true? (:radio-conformity-certified? (store/device-unit s "device-1"))))
        (is (= 1 (count (store/certificate-history s))))
        (is (= 1 (store/next-certificate-sequence s "JPN")))
        (is (true? (store/device-unit-already-certified? s "device-1")))
        (is (false? (store/device-unit-already-certified? s "device-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/device-unit s "nope")))
    (is (= [] (store/all-device-units s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/shipment-history s)))
    (is (= [] (store/certificate-history s)))
    (is (zero? (store/next-shipment-sequence s "JPN")))
    (is (zero? (store/next-certificate-sequence s "JPN")))
    (store/with-device-units s {"x" {:id "x" :device-unit-name "n" :rf-power-deviation-actual 0.3
                                      :rf-power-deviation-min -1.5 :rf-power-deviation-max 1.5
                                      :eol-defect-unresolved? false
                                      :device-unit-shipped? false :radio-conformity-certified? false
                                      :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:device-unit-name (store/device-unit s "x"))))))
