(ns commsdevice.full-supply-chain-integration-test
  "ADR-2607999980's headline proof: the GENUINE full 2-hop, 3-actor
  smartphone supply-chain-pedigree chain --

    cloud-itonami-isic-0729 (non-ferrous metal ore mining)
      -> cloud-itonami-isic-2610 (semiconductor/electronics fab)
      -> cloud-itonami-isic-2630 (THIS actor, communication-equipment
         assembly)

  -- built end to end from REAL cross-repo calls into every upstream
  actor's OWN real export/store/robotics functions (never a
  hand-written EDN literal that merely mimics what those functions
  would produce), fed into THIS actor's UNMODIFIED-SINCE-LANDING
  governor (`commsdevice.governor`'s `upstream-component-pedigrees-
  claims-out-of-tolerance-violations`, landed by THIS SAME ADR, is
  NOT touched again by this test file -- it exists to VERIFY, not
  assume, that its existing recursive `kotoba.pedigree/valid?` shape
  check and its existing top-level `:bond-pull-strength-gf` claim
  check correctly handle a component pedigree whose `:pedigree/
  upstream` chain is genuinely TWO levels deep, mirroring
  ADR-2607999970's `automotive.full-supply-chain-integration-test`
  one link earlier in the automotive chain this file direct-ports).

  This repo's `:cross-repo-test` alias (see deps.edn) declares TWO
  `:local/root` siblings (`cloud-itonami-isic-2610`, `cloud-itonami-
  isic-0729`) directly -- practical here because tools.deps does not
  compose another `:local/root` dependency's own alias-scoped deps
  (isic-2610's OWN dependency on isic-0729 is invisible from here),
  the SAME mechanism `automotive.full-supply-chain-integration-test`
  already established for a 3-sibling, 3-hop chain one link earlier;
  this file needs only 2 siblings for a 2-hop chain.

  Run with `clojure -M:dev:cross-repo-test`. Still no live network
  call between actors at runtime: this is a build-time classpath
  dependency exercised by tests, same category as every other
  `:local/root` dependency in this fleet."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.pedigree :as pedigree]
            [langgraph.graph :as g]
            [nonferrousops.export :as ore-export]
            [nonferrousops.store :as ore-store]
            [fab.export :as lot-export]
            [fab.robotics :as lot-robotics]
            [commsdevice.governor :as governor]
            [commsdevice.store :as store]
            [commsdevice.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :radio-compliance-engineer :phase 3})

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

(defn- real-ore-pedigree
  "Hop 1, THE genuine cross-repo call into cloud-itonami-isic-0729:
  writes a real production record into a real `nonferrousops.store`
  MemStore, reads it back out via that repo's OWN store protocol, and
  packages it via that repo's OWN `nonferrousops.export/pedigree-for-
  production-record` -- never a hand-typed EDN literal."
  [record-id grade-actual quantity-tonnes issued-at]
  (let [st (ore-store/mem-store)
        st' (ore-store/add-production-record st record-id
                                              {:site-id "copper-site-001"
                                               :commodity :copper
                                               :grade-actual grade-actual
                                               :grade-min 0.0 :grade-max 100.0
                                               :quantity-tonnes quantity-tonnes})
        rec (ore-store/production-record st' record-id)]
    (ore-export/pedigree-for-production-record rec issued-at)))

(defn- real-fab-lot-pedigree
  "Hop 2, THE genuine cross-repo call into cloud-itonami-isic-2610:
  builds a real fab-lot record, runs that repo's OWN real `physics-2d`
  wire-bond pull-test simulation, and packages the result via that
  repo's OWN `fab.export/pedigree-for-lot` -- optionally embedding an
  upstream ore pedigree first, exactly as isic-2610's own governor
  requires before a real lot may dispatch."
  ([lot-id bond-wire-diameter-um issued-at]
   (real-fab-lot-pedigree lot-id bond-wire-diameter-um issued-at nil))
  ([lot-id bond-wire-diameter-um issued-at upstream-ore-pedigree]
   (let [base (merge {:id lot-id :bond-wire-diameter-um bond-wire-diameter-um}
                      (lot-robotics/bond-pull-telemetry-for {:bond-wire-diameter-um bond-wire-diameter-um}))
         lot (cond-> base
               (some? upstream-ore-pedigree) (assoc :upstream-ore-pedigree upstream-ore-pedigree))]
     (lot-export/pedigree-for-lot lot issued-at))))

(deftest genuine-2-hop-3-actor-chain-is-shape-valid-end-to-end
  (testing "ore -> fab lot, both hops genuine cross-repo calls, is shape-valid end-to-end and genuinely 2 levels deep from the component pedigree's own vantage point"
    (let [ore (real-ore-pedigree "prod-strong" 26.5 3600.0 "2026-07-16")
          component (real-fab-lot-pedigree "lot-strong" 25.0 "2026-07-16" ore)]
      (is (true? (pedigree/valid? ore)))
      (is (true? (pedigree/valid? component)))
      (is (= ore (:pedigree/upstream component))
          "the REAL isic-0729 pedigree is embedded verbatim at depth 1")
      (testing "each hop's own claim stays independently readable at its own depth"
        (is (= 26.5 (pedigree/claim-value (:pedigree/upstream component) :grade-actual)))
        (is (number? (pedigree/claim-value component :bond-pull-strength-gf)))))))

(deftest real-3-actor-chain-genuinely-clears-commsdevice-governor-with-no-commsdevice-side-code-change
  (testing "a genuinely 2-level-deep upstream chain (fab lot embeds ore) clears commsdevice.governor's independent acceptance check end-to-end, and a real device-unit ships -- proving kotoba.pedigree/valid?'s recursive verification, not any new commsdevice-side code, is what makes the deeper chain work"
    (let [ore (real-ore-pedigree "prod-strong" 26.5 3600.0 "2026-07-16")
          component-pedigree (real-fab-lot-pedigree "lot-strong" 25.0 "2026-07-16" ore)
          _ (is (>= (pedigree/claim-value component-pedigree :bond-pull-strength-gf) governor/min-upstream-component-bond-pull-strength-gf)
                "sanity: this fab lot's REAL simulated bond-pull strength actually clears commsdevice's own disclosed floor")
          db (store/seed-db)
          actor (op/build db)]
      (verify! actor "e1pre" "device-1")
      (simulate-robotics! actor "e1pre2" "device-1")
      (attach-pedigrees! actor "e1pre3" "device-1" [component-pedigree])
      (is (= [component-pedigree] (:upstream-component-pedigrees (store/device-unit db "device-1")))
          "the REAL 2-hop cross-repo pedigree landed on the device-unit record unmodified")
      (let [res (exec-op actor "e1" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
        (is (= :interrupted (:status res))
            "governor's independent re-verification found no violation from the real 2-hop pedigree -- escalates for human approval, same as any clean shipment")
        (let [r2 (approve! actor "e1")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:device-unit-shipped? (store/device-unit db "device-1")))))))))

(deftest real-3-actor-chain-with-shape-invalid-ore-poisons-the-whole-chain-and-is-caught-by-commsdevices-existing-check
  (testing "a shape-invalid ore pedigree ONE LEVEL DEEP (never a top-level defect) still makes the top-level component pedigree fail kotoba.pedigree/valid?, and commsdevice.governor's independent :upstream-component-pedigree-invalid-shape check -- which only ever calls pedigree/valid? on the TOP-level entry -- genuinely catches it via that recursive check"
    (let [ore (real-ore-pedigree "prod-corrupt" 26.5 3600.0 "2026-07-16")
          bad-ore (assoc ore :pedigree/claims {:grade-actual "high"})
          component-pedigree (real-fab-lot-pedigree "lot-strong2" 25.0 "2026-07-16" bad-ore)
          db (store/seed-db)
          actor (op/build db)]
      (is (false? (pedigree/valid? bad-ore)) "sanity: the corrupted ore fixture really is shape-invalid on its own")
      (is (false? (pedigree/valid? component-pedigree)) "the component pedigree embedding it is ALSO invalid -- valid? poisons the whole chain")
      (verify! actor "e2pre" "device-1")
      (simulate-robotics! actor "e2pre2" "device-1")
      (attach-pedigrees! actor "e2pre3" "device-1" [component-pedigree])
      (let [res (exec-op actor "e2" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:upstream-component-pedigree-invalid-shape} (-> (store/ledger db) last :basis)))
        (is (empty? (store/shipment-history db)))))))

(deftest real-3-actor-chain-components-own-floor-still-gates-regardless-of-upstream-strength
  (testing "commsdevice's acceptance check is scoped to the COMPONENT pedigree's OWN top-level claim, never a deeper embedded claim -- a too-weak fab lot still HARD-holds even when its embedded ore hop is individually strong, exactly as this ADR's own governor-in-isolation tests already establish (no regression, no new behavior introduced by the deeper chain)"
    (let [ore (real-ore-pedigree "prod-strong3" 26.5 3600.0 "2026-07-16")
          component-pedigree (real-fab-lot-pedigree "lot-weak" 12.0 "2026-07-16" ore)
          _ (is (< (pedigree/claim-value component-pedigree :bond-pull-strength-gf) governor/min-upstream-component-bond-pull-strength-gf)
                "sanity: this fab lot's REAL simulated bond-pull strength falls short of commsdevice's own disclosed floor, despite a strong upstream ore hop")
          db (store/seed-db)
          actor (op/build db)]
      (verify! actor "e3pre" "device-1")
      (simulate-robotics! actor "e3pre2" "device-1")
      (attach-pedigrees! actor "e3pre3" "device-1" [component-pedigree])
      (let [res (exec-op actor "e3" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:upstream-component-pedigree-claims-out-of-tolerance} (-> (store/ledger db) last :basis)))
        (is (empty? (store/shipment-history db)))))))
