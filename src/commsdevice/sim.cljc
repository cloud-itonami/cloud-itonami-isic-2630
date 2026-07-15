(ns commsdevice.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean device-unit
  through intake -> radio-compliance-rules requirements verification ->
  end-of-line-defect screening -> robot display-bonding-press
  simulation -> device-unit-shipment proposal (always escalates) ->
  human approval -> commit, then through radio-conformity-certificate
  proposal (always escalates) -> human approval -> commit, then shows
  seven HARD holds (a jurisdiction with no spec-basis, a device-unit-
  shipment attempt with no evidence verification on file, a device-unit-
  shipment attempt before the robot display-bonding-press mission ever
  ran, an out-of-spec RF conducted-power deviation, a device-unit whose
  robotics-sim is already on file but whose REAL physics-2d-simulated
  bonding-pressure telemetry falls out of tolerance on independent
  recheck, an unresolved end-of-line defect screened directly via
  `:end-of-line-quality/screen` [never via an actuation op against an
  unscreened device-unit -- see this actor's own governor ns docstring
  / the lesson every prior sibling's ADR-0001 already recorded], and a
  double device-unit-shipment/certificate-issuance of an already-
  processed device-unit) that never reach a human at all, and prints
  the audit ledger + the draft device-unit-shipment and radio-
  conformity-certificate records."
  (:require [langgraph.graph :as g]
            [commsdevice.export :as export]
            [commsdevice.store :as store]
            [commsdevice.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :radio-compliance-engineer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== device-unit/intake device-1 (JPN, clean; RF power within spec, no EOL defect) ==")
    (println (exec! actor "t1" {:op :device-unit/intake :subject "device-1"
                                :patch {:id "device-1" :device-unit-name "Amanogawa Handset AH-12 (JPN lot)"}} operator))

    (println "== radio-compliance-rules/verify device-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :radio-compliance-rules/verify :subject "device-1"} operator))
    (println (approve! actor "t2"))

    (println "== end-of-line-quality/screen device-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :end-of-line-quality/screen :subject "device-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/ship-device-unit device-1 before robotics simulation -> HARD hold (robotics-simulation-missing) ==")
    (println (exec! actor "t3a" {:op :actuation/ship-device-unit :subject "device-1"} operator))

    (println "== robotics/simulate-display-bonding device-1 (real physics-2d press mission; escalates -- human approves) ==")
    (println (exec! actor "t3b" {:op :robotics/simulate-display-bonding :subject "device-1"} operator))
    (println (approve! actor "t3b"))

    (println "== actuation/ship-device-unit device-1 (always escalates -- actuation/ship-device-unit) ==")
    (let [r (exec! actor "t4" {:op :actuation/ship-device-unit :subject "device-1"} operator)]
      (println r)
      (println "-- human radio-compliance engineer approves --")
      (println (approve! actor "t4")))

    (println "== actuation/issue-radio-conformity-certificate device-1 (always escalates -- actuation/issue-radio-conformity-certificate) ==")
    (let [r (exec! actor "t5" {:op :actuation/issue-radio-conformity-certificate :subject "device-1"} operator)]
      (println r)
      (println "-- human radio-compliance engineer approves --")
      (println (approve! actor "t5")))

    (println "== radio-compliance-rules/verify device-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :radio-compliance-rules/verify :subject "device-2" :no-spec? true} operator))

    (println "== actuation/ship-device-unit device-3 with no evidence verification on file -> HARD hold (evidence-incomplete) ==")
    (println (exec! actor "t6b" {:op :actuation/ship-device-unit :subject "device-3"} operator))

    (println "== radio-compliance-rules/verify device-3 (escalates -- human approves; sets up the out-of-spec test) ==")
    (println (exec! actor "t7" {:op :radio-compliance-rules/verify :subject "device-3"} operator))
    (println (approve! actor "t7"))

    (println "== robotics/simulate-display-bonding device-3 (clean bonding pressure; escalates -- human approves) ==")
    (println (exec! actor "t7c" {:op :robotics/simulate-display-bonding :subject "device-3"} operator))
    (println (approve! actor "t7c"))

    (println "== actuation/ship-device-unit device-3 (3.5dB outside [-1.5,1.5]dB RF-power tolerance -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/ship-device-unit :subject "device-3"} operator))

    (println "== actuation/ship-device-unit device-5 (robotics-sim on file, but bonding-press platen mass genuinely too heavy -> real sim-peak-bonding-pressure-mpa out of tolerance on independent recheck -> HARD hold) ==")
    (println (exec! actor "t8b" {:op :radio-compliance-rules/verify :subject "device-5"} operator))
    (println (approve! actor "t8b"))
    (println (exec! actor "t8c" {:op :actuation/ship-device-unit :subject "device-5"} operator))

    (println "== end-of-line-quality/screen device-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :end-of-line-quality/screen :subject "device-4"} operator))

    (println "== actuation/ship-device-unit device-1 AGAIN (double-shipment -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/ship-device-unit :subject "device-1"} operator))

    (println "== actuation/issue-radio-conformity-certificate device-1 AGAIN (double-issuance -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/issue-radio-conformity-certificate :subject "device-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft device-unit-shipment records ==")
    (doseq [r (store/shipment-history db)] (println r))

    (println "== draft radio-conformity-certificate records ==")
    (doseq [r (store/certificate-history db)] (println r))

    (println "== social hand-off: audit package counts ==")
    (println (:counts (export/audit-package db)))
    (println "== social hand-off: CSV bundle keys ==")
    (println (keys (export/package->csv-bundle db)))))
