(ns commsdevice.upstream-component-pedigrees-test
  "ADR-2607999980's cross-actor supply-chain-linkage check
  (`commsdevice.governor/upstream-component-pedigrees-claims-out-of-
  tolerance-violations`, direct port of ADR-2607999960's `automotive.
  governor` equivalent), exercised with HAND-BUILT `kotoba.pedigree`
  records (via the real `kotoba.pedigree/claim` constructor -- never a
  raw map literal that merely LOOKS like a pedigree). The genuine
  cross-repo proof -- actual calls into `cloud-itonami-isic-2610`'s
  `fab.export/pedigree-for-lot` (and, for the full 2-hop chain,
  `cloud-itonami-isic-0729`'s `nonferrousops.export/pedigree-for-
  production-record`) -- lives in `test-cross-repo/commsdevice/
  full_supply_chain_integration_test.clj` (a separate alias, see
  deps.edn); this file only proves the GOVERNOR check itself is
  correct in isolation, independent of which upstream actor produced
  the pedigree."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.pedigree :as pedigree]
            [langgraph.graph :as g]
            [commsdevice.governor :as governor]
            [commsdevice.store :as store]
            [commsdevice.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :radio-compliance-engineer :phase 3})

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify! [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :radio-compliance-rules/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- simulate-robotics! [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-robotics") {:op :robotics/simulate-display-bonding :subject subject} operator)
  (approve! actor (str tid-prefix "-robotics")))

(defn- attach-pedigrees! [actor tid-prefix subject pedigrees]
  (exec-op actor (str tid-prefix "-pedigrees")
           {:op :device-unit/intake :subject subject
            :patch {:id subject :upstream-component-pedigrees pedigrees}}
           operator))

(defn- clean-pedigree
  ([] (clean-pedigree "PEDIGREE-lot-1" "lot-1"))
  ([id subject-lot-id]
   (pedigree/claim id subject-lot-id "cloud-itonami-isic-2610"
                    {:bond-pull-strength-gf (+ governor/min-upstream-component-bond-pull-strength-gf 3.0)}
                    :evidence-basis ["fab.robotics/bond-pull-telemetry-for"]
                    :issued-at "2026-07-16")))

(defn- weak-pedigree
  ([] (weak-pedigree "PEDIGREE-lot-2" "lot-2"))
  ([id subject-lot-id]
   (pedigree/claim id subject-lot-id "cloud-itonami-isic-2610"
                    {:bond-pull-strength-gf (- governor/min-upstream-component-bond-pull-strength-gf 3.0)}
                    :evidence-basis ["fab.robotics/bond-pull-telemetry-for"]
                    :issued-at "2026-07-16")))

(deftest absent-upstream-component-pedigrees-is-a-no-op
  (testing "a device-unit with no :upstream-component-pedigrees ships exactly as before this ADR -- no new violation"
    (let [[db actor] (fresh)
          _ (verify! actor "t1pre" "device-1")
          _ (simulate-robotics! actor "t1pre2" "device-1")
          res (exec-op actor "t1" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (is (nil? (:upstream-component-pedigrees (store/device-unit db "device-1"))))
      (is (= :interrupted (:status res)) "still escalates for human approval, same as before -- no HARD hold introduced")
      (let [r2 (approve! actor "t1")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:device-unit-shipped? (store/device-unit db "device-1"))))))))

(deftest empty-upstream-component-pedigrees-is-also-a-no-op
  (testing "an explicitly empty :upstream-component-pedigrees vector is likewise a no-op, not a HARD hold"
    (let [[_db actor] (fresh)
          _ (verify! actor "t2pre" "device-1")
          _ (simulate-robotics! actor "t2pre2" "device-1")
          _ (attach-pedigrees! actor "t2pre3" "device-1" [])
          res (exec-op actor "t2" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))))))

(deftest valid-in-tolerance-upstream-component-pedigrees-ship-normally
  (testing "shape-valid pedigrees whose claims clear the acceptance floor do not block shipment"
    (let [[db actor] (fresh)
          _ (verify! actor "t3pre" "device-1")
          _ (simulate-robotics! actor "t3pre2" "device-1")
          _ (attach-pedigrees! actor "t3pre3" "device-1" [(clean-pedigree)])
          res (exec-op actor "t3" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (is (= 1 (count (:upstream-component-pedigrees (store/device-unit db "device-1")))))
      (is (= :interrupted (:status res)) "still escalates for human approval -- actuation is never auto")
      (let [r2 (approve! actor "t3")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:device-unit-shipped? (store/device-unit db "device-1"))))))))

(deftest multiple-valid-pedigrees-all-clearing-ship-normally
  (testing "a device-unit with SEVERAL upstream component pedigrees, all clearing the floor, ships normally -- a smartphone has many components"
    (let [[db actor] (fresh)
          _ (verify! actor "t4pre" "device-1")
          _ (simulate-robotics! actor "t4pre2" "device-1")
          _ (attach-pedigrees! actor "t4pre3" "device-1"
                                [(clean-pedigree "PEDIGREE-lot-1" "lot-1")
                                 (clean-pedigree "PEDIGREE-lot-3" "lot-3")])
          res (exec-op actor "t4" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (is (= 2 (count (:upstream-component-pedigrees (store/device-unit db "device-1")))))
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t4")]
        (is (= :commit (get-in r2 [:state :disposition])))))))

(deftest upstream-component-pedigree-claims-out-of-tolerance-is-held
  (testing "a shape-valid pedigree whose claim falls below the acceptance floor -> HARD hold, independent of RF power/robotics/evidence being otherwise clean"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "device-1")
          _ (simulate-robotics! actor "t5pre2" "device-1")
          _ (attach-pedigrees! actor "t5pre3" "device-1" [(weak-pedigree)])
          res (exec-op actor "t5" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:upstream-component-pedigree-claims-out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest one-weak-pedigree-among-many-still-holds
  (testing "ONE out-of-tolerance entry among several otherwise-clean upstream component pedigrees still HARD-holds -- every component must independently clear"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "device-1")
          _ (simulate-robotics! actor "t6pre2" "device-1")
          _ (attach-pedigrees! actor "t6pre3" "device-1"
                                [(clean-pedigree "PEDIGREE-lot-1" "lot-1")
                                 (weak-pedigree "PEDIGREE-lot-2" "lot-2")])
          res (exec-op actor "t6" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:upstream-component-pedigree-claims-out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest upstream-component-pedigree-invalid-shape-is-held
  (testing "an attached map that fails kotoba.pedigree/valid? (e.g. a non-numeric claim, mimicking a self-reported string) -> HARD hold, never trusted at face value"
    (let [[db actor] (fresh)
          bad-pedigree (assoc (clean-pedigree) :pedigree/claims {:bond-pull-strength-gf "plenty"})
          _ (verify! actor "t7pre" "device-1")
          _ (simulate-robotics! actor "t7pre2" "device-1")
          _ (attach-pedigrees! actor "t7pre3" "device-1" [bad-pedigree])
          res (exec-op actor "t7" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (is (false? (pedigree/valid? bad-pedigree)) "sanity: the fixture really is shape-invalid")
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:upstream-component-pedigree-invalid-shape} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest upstream-component-pedigree-with-invalid-nested-upstream-is-held
  (testing "a shape-valid fab-lot pedigree embedding a SHAPE-INVALID nested :pedigree/upstream (e.g. a malformed ore pedigree) -> HARD hold -- recursive re-verification catches a bad link at ANY hop of the chain, not just the immediate one"
    (let [[db actor] (fresh)
          bad-ore (pedigree/claim "PEDIGREE-prod-1" "prod-1" "cloud-itonami-isic-0729"
                                   {:grade-actual "high"}
                                   :evidence-basis ["nonferrousops.store/production-record"]
                                   :issued-at "2026-07-16")
          component-pedigree (pedigree/claim "PEDIGREE-lot-1" "lot-1" "cloud-itonami-isic-2610"
                                              {:bond-pull-strength-gf (+ governor/min-upstream-component-bond-pull-strength-gf 3.0)}
                                              :evidence-basis ["fab.robotics/bond-pull-telemetry-for"]
                                              :issued-at "2026-07-16"
                                              :upstream bad-ore)
          _ (verify! actor "t8pre" "device-1")
          _ (simulate-robotics! actor "t8pre2" "device-1")
          _ (attach-pedigrees! actor "t8pre3" "device-1" [component-pedigree])
          res (exec-op actor "t8" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (is (false? (pedigree/valid? bad-ore)) "sanity: the embedded upstream really is shape-invalid on its own")
      (is (false? (pedigree/valid? component-pedigree)) "sanity: a poisoned nested upstream poisons the whole chain")
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:upstream-component-pedigree-invalid-shape} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest upstream-component-pedigrees-check-scoped-to-ship-device-unit-op
  (testing "the check only fires for :actuation/ship-device-unit -- an out-of-tolerance pedigree already on file does not block an unrelated op"
    (let [[_db actor] (fresh)
          _ (attach-pedigrees! actor "t9pre" "device-1" [(weak-pedigree)])
          res (exec-op actor "t9" {:op :radio-compliance-rules/verify :subject "device-1"} operator)]
      (is (= :interrupted (:status res)) "radio-compliance-rules/verify is unaffected by an out-of-tolerance upstream component pedigree")
      (let [r2 (approve! actor "t9")]
        (is (= :commit (get-in r2 [:state :disposition])))))))
