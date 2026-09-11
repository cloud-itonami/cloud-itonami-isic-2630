(ns commsdevice.governor-contract-test
  "The governor contract as executable tests -- the communication-
  equipment-manufacturer analog of `cloud-itonami-isic-6512`'s
  `casualty.governor-contract-test`. The single invariant under test:

    Device Advisor never ships a device-unit action or issues a radio-
    conformity certificate the Radio-Compliance Governor would reject,
    `:actuation/ship-device-unit`/`:actuation/issue-radio-conformity-
    certificate` NEVER auto-commit at any phase, `:device-unit/intake`
    (no direct capital risk) MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [commsdevice.store :as store]
            [commsdevice.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :radio-compliance-engineer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a radio-
  compliance-rules evidence verification on file. Uses distinct
  thread-ids per call site by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :radio-compliance-rules/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through end-of-line screening -> approve, leaving a
  screening on file. Only safe to call for a device-unit whose defect
  status has already resolved -- an unresolved defect HARD-holds the
  screen itself (see `end-of-line-defect-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :end-of-line-quality/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(defn- simulate-robotics!
  "Walks `subject` through the robot display-bonding-press verification
  mission -> approve, leaving `:robotics-sim-verified?` on file. Only
  meaningful to call for a device-unit whose REAL `physics-2d`-
  simulated bonding-pressure telemetry is actually within tolerance --
  an out-of-tolerance device-unit still gets `:robotics-sim-verified?`
  recorded (per whatever the mission itself found), but
  `commsdevice.governor`'s independent recheck HARD-holds regardless
  (see `robotics-simulation-out-of-tolerance-is-held`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-robotics") {:op :robotics/simulate-display-bonding :subject subject} operator)
  (approve! actor (str tid-prefix "-robotics")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :device-unit/intake :subject "device-1"
                   :patch {:id "device-1" :device-unit-name "Amanogawa Handset AH-12 (JPN lot)"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Amanogawa Handset AH-12 (JPN lot)" (:device-unit-name (store/device-unit db "device-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest requirements-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :radio-compliance-rules/verify :subject "device-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/requirements-verification-of db "device-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a radio-compliance-rules/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :radio-compliance-rules/verify :subject "device-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/requirements-verification-of db "device-1")) "no verification written"))))

(deftest ship-device-unit-without-verification-is-held
  (testing "actuation/ship-device-unit before any radio-compliance evidence verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest device-rf-power-out-of-range-is-held
  (testing "a device-unit whose own RF conducted-power deviation falls outside its own production-test tolerance band -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "device-3")
          _ (simulate-robotics! actor "t5pre2" "device-3")
          res (exec-op actor "t5" {:op :actuation/ship-device-unit :subject "device-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:device-rf-power-out-of-range} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest end-of-line-defect-is-held-and-unoverridable
  (testing "an unresolved end-of-line defect on a device-unit -> HOLD, and never reaches request-approval -- exercised via :end-of-line-quality/screen DIRECTLY, not via the actuation op against an unscreened device-unit (see this actor's governor ns docstring / every prior sibling's ADR-0001)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :end-of-line-quality/screen :subject "device-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:end-of-line-defect-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/eol-screen-of db "device-4")) "no clearance written"))))

(deftest ship-device-unit-always-escalates-then-human-decides
  (testing "a clean, fully-verified, in-spec device-unit still ALWAYS interrupts for human approval -- actuation/ship-device-unit is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "device-1")
          _ (simulate-robotics! actor "t7pre2" "device-1")
          r1 (exec-op actor "t7" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, shipment record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:device-unit-shipped? (store/device-unit db "device-1"))))
          (is (= 1 (count (store/shipment-history db))) "one draft shipment record"))))))

(deftest issue-radio-conformity-certificate-always-escalates-then-human-decides
  (testing "a clean, fully-verified, resolved-defect device-unit still ALWAYS interrupts for human approval -- actuation/issue-radio-conformity-certificate is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "device-1")
          _ (screen! actor "t8pre2" "device-1")
          r1 (exec-op actor "t8" {:op :actuation/issue-radio-conformity-certificate :subject "device-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, certificate record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:radio-conformity-certified? (store/device-unit db "device-1"))))
          (is (= 1 (count (store/certificate-history db))) "one draft certificate record"))))))

(deftest ship-device-unit-double-shipment-is-held
  (testing "shipping the same device-unit's action twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "device-1")
          _ (simulate-robotics! actor "t9pre2" "device-1")
          _ (exec-op actor "t9a" {:op :actuation/ship-device-unit :subject "device-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-shipped} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/shipment-history db))) "still only the one earlier shipment"))))

(deftest issue-radio-conformity-certificate-double-issuance-is-held
  (testing "issuing the same device-unit's radio-conformity certificate twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "device-1")
          _ (screen! actor "t10pre2" "device-1")
          _ (exec-op actor "t10a" {:op :actuation/issue-radio-conformity-certificate :subject "device-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/issue-radio-conformity-certificate :subject "device-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-certified} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/certificate-history db))) "still only the one earlier certificate issuance"))))

(deftest robotics-simulation-always-needs-approval
  (testing "robotics/simulate-display-bonding is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t11" {:op :robotics/simulate-display-bonding :subject "device-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t11")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:robotics-sim-verified? (store/device-unit db "device-1"))))))))

(deftest ship-device-unit-without-robotics-simulation-is-held
  (testing "actuation/ship-device-unit before the robot display-bonding-press mission ever ran -> HOLD (robotics-simulation-missing)"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "device-1")
          res (exec-op actor "t12" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:robotics-simulation-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest robotics-simulation-out-of-tolerance-is-held
  (testing "device-5 has a robotics-sim already on file, but its own REAL physics-2d-simulated display-bonding-press reading falls outside the acceptance band on INDEPENDENT recheck -> HOLD, never trusts the on-file verdict alone"
    (let [[db actor] (fresh)
          _ (verify! actor "t13pre" "device-5")
          res (exec-op actor "t13" {:op :actuation/ship-device-unit :subject "device-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:robotics-simulation-out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :device-unit/intake :subject "device-1"
                          :patch {:id "device-1" :device-unit-name "Amanogawa Handset AH-12 (JPN lot)"}} operator)
      (exec-op actor "b" {:op :radio-compliance-rules/verify :subject "device-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
