(ns commsdevice.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [commsdevice.registry :as registry]))

(deftest device-rf-power-out-of-range-is-two-sided
  (testing "within bounds -> false"
    (is (not (registry/device-rf-power-out-of-range?
              {:rf-power-deviation-actual 0.3 :rf-power-deviation-min -1.5 :rf-power-deviation-max 1.5}))))
  (testing "above max -> true"
    (is (registry/device-rf-power-out-of-range?
         {:rf-power-deviation-actual 3.5 :rf-power-deviation-min -1.5 :rf-power-deviation-max 1.5})))
  (testing "below min -> true"
    (is (registry/device-rf-power-out-of-range?
         {:rf-power-deviation-actual -3.5 :rf-power-deviation-min -1.5 :rf-power-deviation-max 1.5})))
  (testing "missing fields -> false (no fabricated verdict)"
    (is (not (registry/device-rf-power-out-of-range? {})))))

(deftest register-device-unit-shipment-validates-required-fields
  (is (thrown? Exception
               (registry/register-device-unit-shipment nil "JPN" 0)))
  (is (thrown? Exception
               (registry/register-device-unit-shipment "device-1" nil 0)))
  (is (thrown? Exception
               (registry/register-device-unit-shipment "device-1" "JPN" -1))))

(deftest register-device-unit-shipment-shape
  (let [{:strs [record shipment_number] :as result} (registry/register-device-unit-shipment "device-1" "jpn" 0)]
    (is (= "JPN-SHP-000000" shipment_number))
    (is (= "device-unit-shipment-draft" (get record "kind")))
    (is (= "device-1" (get record "device_unit_id")))
    (is (= "draft-unsigned" (get-in result ["certificate" "status"])))))

(deftest register-radio-conformity-certificate-validates-required-fields
  (is (thrown? Exception
               (registry/register-radio-conformity-certificate nil "JPN" 0)))
  (is (thrown? Exception
               (registry/register-radio-conformity-certificate "device-1" nil 0)))
  (is (thrown? Exception
               (registry/register-radio-conformity-certificate "device-1" "JPN" -1))))

(deftest register-radio-conformity-certificate-shape
  (let [{:strs [record certificate_number] :as result} (registry/register-radio-conformity-certificate "device-1" "jpn" 0)]
    (is (= "JPN-RCC-000000" certificate_number))
    (is (= "radio-conformity-certificate-draft" (get record "kind")))
    (is (= "device-1" (get record "device_unit_id")))
    (is (= "draft-unsigned" (get-in result ["certificate" "status"])))))

(deftest append-is-append-only
  (is (= [{:a 1} {:b 2}] (registry/append [{:a 1}] {"record" {:b 2}}))))
