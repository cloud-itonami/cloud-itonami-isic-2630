(ns commsdevice.store
  "SSoT for the communication-equipment-manufacturing actor, behind a
  `Store` protocol so the backend is a swap, not a rewrite -- the same
  seam every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/commsdevice/store_contract_test.clj), which is the whole point:
  the actor, the Radio-Compliance Governor and the audit ledger never
  know which SSoT they run on.

  Like `automotive.store`'s dual vehicle-dispatch/conformity-certificate
  history and every other dual-actuation sibling before it, this actor
  has TWO actuation events (shipping a device-unit, issuing a radio-
  conformity certificate) acting on the SAME entity (a device-unit),
  each with its OWN history collection, sequence counter and dedicated
  double-actuation-guard boolean (`:device-unit-shipped?`/`:radio-
  conformity-certified?`, never a `:status` value) -- the same
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320).

  The ledger stays append-only on every backend: 'which device-unit was
  screened for an unresolved end-of-line defect, which device-unit
  shipment was dispatched, which radio-conformity certificate was
  issued, on what jurisdictional basis, approved by whom' is always a
  query over an immutable log -- the audit trail a community trusting a
  communication-equipment manufacturer needs, and the evidence a
  manufacturer needs if a shipment or radio-conformity-certificate
  decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [commsdevice.registry :as registry]
            [commsdevice.robotics :as robotics]
            [langchain.db :as d]))

(defprotocol Store
  (device-unit [s id])
  (all-device-units [s])
  (eol-screen-of [s device-unit-id] "committed end-of-line-defect screening verdict for a device-unit, or nil")
  (requirements-verification-of [s device-unit-id] "committed radio-compliance-rules verification, or nil")
  (ledger [s])
  (shipment-history [s] "the append-only device-unit-shipment history (commsdevice.registry drafts)")
  (certificate-history [s] "the append-only radio-conformity-certificate history (commsdevice.registry drafts)")
  (next-shipment-sequence [s jurisdiction] "next shipment-number sequence for a jurisdiction")
  (next-certificate-sequence [s jurisdiction] "next certificate-number sequence for a jurisdiction")
  (device-unit-already-shipped? [s device-unit-id] "has this device-unit already been shipped?")
  (device-unit-already-certified? [s device-unit-id] "has this device-unit's radio-conformity certificate already been issued?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-device-units [s device-units] "replace/seed the device-unit directory (map id->device-unit)"))

;; ----------------------------- demo data -----------------------------

(defn- with-bonding-telemetry
  "Merges REAL display-module optical-bonding (OCA-lamination) press
  telemetry onto a demo device-unit's base fields --
  `commsdevice.robotics/bonding-telemetry-for` actually runs
  `simulate-bonding-press`'s `physics-2d`-stepped simulation for this
  device-unit's own `:bonding-press-platen-mass-kg`, so even the
  'already on file' seed data (as if from an earlier real press-run
  report) is genuinely simulation-derived, never hand-typed doubles."
  [base]
  (merge base (select-keys (robotics/bonding-telemetry-for base)
                           [:sim-peak-bonding-force-n :sim-peak-bonding-pressure-mpa])))

(defn demo-data
  "A small, self-contained device-unit set covering both actuation
  lifecycles (shipping a device-unit, issuing a radio-conformity
  certificate) so the actor + tests run offline.
  `:bonding-press-platen-mass-kg` is a permanent device-unit press-run
  field (like `:rf-power-deviation-actual`); `:sim-peak-bonding-force-
  n`/`:sim-peak-bonding-pressure-mpa` are the REAL `commsdevice.
  robotics/simulate-bonding-press`-computed telemetry for that field
  (`with-bonding-telemetry`), the ground truth `commsdevice.robotics/
  simulation-out-of-tolerance?` independently rechecks. device-5 (a
  device-unit whose display-bonding-press record is already marked
  verified) is DELIBERATELY recorded with a much heavier
  `:bonding-press-platen-mass-kg` (45 kg) than this bonding process
  should carry -- a genuine design-record inconsistency (no real
  OCA-lamination press for this display class should run at anywhere
  near this platen mass -- it would squeeze out the adhesive/damage the
  stack) that the real, re-run simulation catches on independent
  recheck even though `:robotics-sim-verified?` was seeded `true`
  ('already on file', i.e. someone/something marked it passed without
  this real check ever having run) -- the communication-equipment
  analog of automotive's vehicle-5 misclassified pickup / autoparts'
  lot-5 misclassified fastener lot. device-1..4's `:bonding-press-
  platen-mass-kg` values (18-20 kg) are all genuinely consistent
  press-run masses, which all clear the real bonding-pressure band
  with margin (see `commsdevice.robotics/min-bonding-pressure-mpa`/
  `max-bonding-pressure-mpa`)."
  []
  {:device-units
   (into {}
         (map (fn [v] [(:id v) (with-bonding-telemetry v)]))
         [{:id "device-1" :device-unit-name "Amanogawa Handset AH-12 (JPN lot)"
           :rf-power-deviation-actual 0.3 :rf-power-deviation-min -1.5 :rf-power-deviation-max 1.5
           :bonding-press-platen-mass-kg 18.0
           :bonding-pressure-min-mpa 0.15 :bonding-pressure-max-mpa 0.55
           :eol-defect-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :device-unit-shipped? false :radio-conformity-certified? false
           :jurisdiction "JPN" :status :intake}
          {:id "device-2" :device-unit-name "Atlantis Handset AH-9 (ATL lot)"
           :rf-power-deviation-actual 0.3 :rf-power-deviation-min -1.5 :rf-power-deviation-max 1.5
           :bonding-press-platen-mass-kg 18.0
           :bonding-pressure-min-mpa 0.15 :bonding-pressure-max-mpa 0.55
           :eol-defect-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :device-unit-shipped? false :radio-conformity-certified? false
           :jurisdiction "ATL" :status :intake}
          {:id "device-3" :device-unit-name "鈴木ハンドセット SH-21 (JPN lot)"
           :rf-power-deviation-actual 3.5 :rf-power-deviation-min -1.5 :rf-power-deviation-max 1.5
           :bonding-press-platen-mass-kg 20.0
           :bonding-pressure-min-mpa 0.15 :bonding-pressure-max-mpa 0.55
           :eol-defect-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :device-unit-shipped? false :radio-conformity-certified? false
           :jurisdiction "JPN" :status :intake}
          {:id "device-4" :device-unit-name "田中ハンドセット TH-05 (JPN lot)"
           :rf-power-deviation-actual 0.3 :rf-power-deviation-min -1.5 :rf-power-deviation-max 1.5
           :bonding-press-platen-mass-kg 18.0
           :bonding-pressure-min-mpa 0.15 :bonding-pressure-max-mpa 0.55
           :eol-defect-unresolved? true
           :robotics-sim-verified? false :robotics-sim-record nil
           :device-unit-shipped? false :radio-conformity-certified? false
           :jurisdiction "JPN" :status :intake}
          {:id "device-5" :device-unit-name "佐藤ハンドセット SH-33 (JPN lot)"
           :rf-power-deviation-actual 0.3 :rf-power-deviation-min -1.5 :rf-power-deviation-max 1.5
           :bonding-press-platen-mass-kg 45.0
           :bonding-pressure-min-mpa 0.15 :bonding-pressure-max-mpa 0.55
           :eol-defect-unresolved? false
           :robotics-sim-verified? true :robotics-sim-record nil
           :device-unit-shipped? false :radio-conformity-certified? false
           :jurisdiction "JPN" :status :intake}])})

;; ----------------------------- shared commit logic -----------------------------

(defn- ship-device-unit!
  "Backend-agnostic `:device-unit/mark-shipped` -- looks up the
  device-unit via the protocol and drafts the device-unit-shipment
  record, and returns {:result .. :device-unit-patch ..} for the caller
  to persist."
  [s device-unit-id]
  (let [a (device-unit s device-unit-id)
        seq-n (next-shipment-sequence s (:jurisdiction a))
        result (registry/register-device-unit-shipment device-unit-id (:jurisdiction a) seq-n)]
    {:result result
     :device-unit-patch {:device-unit-shipped? true
                          :shipment-number (get result "shipment_number")}}))

(defn- issue-radio-conformity-certificate!
  "Backend-agnostic `:device-unit/mark-certified` -- looks up the
  device-unit via the protocol and drafts the radio-conformity-
  certificate record, and returns {:result .. :device-unit-patch ..}
  for the caller to persist."
  [s device-unit-id]
  (let [a (device-unit s device-unit-id)
        seq-n (next-certificate-sequence s (:jurisdiction a))
        result (registry/register-radio-conformity-certificate device-unit-id (:jurisdiction a) seq-n)]
    {:result result
     :device-unit-patch {:radio-conformity-certified? true
                          :certificate-number (get result "certificate_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (device-unit [_ id] (get-in @a [:device-units id]))
  (all-device-units [_] (sort-by :id (vals (:device-units @a))))
  (eol-screen-of [_ id] (get-in @a [:eol-screens id]))
  (requirements-verification-of [_ device-unit-id] (get-in @a [:verifications device-unit-id]))
  (ledger [_] (:ledger @a))
  (shipment-history [_] (:shipments @a))
  (certificate-history [_] (:certificates @a))
  (next-shipment-sequence [_ jurisdiction] (get-in @a [:shipment-sequences jurisdiction] 0))
  (next-certificate-sequence [_ jurisdiction] (get-in @a [:certificate-sequences jurisdiction] 0))
  (device-unit-already-shipped? [_ device-unit-id] (boolean (get-in @a [:device-units device-unit-id :device-unit-shipped?])))
  (device-unit-already-certified? [_ device-unit-id] (boolean (get-in @a [:device-units device-unit-id :radio-conformity-certified?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :device-unit/upsert
      (swap! a update-in [:device-units (:id value)] merge value)

      :verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :eol-screen/set
      (swap! a assoc-in [:eol-screens (first path)] payload)

      :device-unit/mark-shipped
      (let [device-unit-id (first path)
            {:keys [result device-unit-patch]} (ship-device-unit! s device-unit-id)
            jurisdiction (:jurisdiction (device-unit s device-unit-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:shipment-sequences jurisdiction] (fnil inc 0))
                       (update-in [:device-units device-unit-id] merge device-unit-patch)
                       (update :shipments registry/append result))))
        result)

      :device-unit/mark-certified
      (let [device-unit-id (first path)
            {:keys [result device-unit-patch]} (issue-radio-conformity-certificate! s device-unit-id)
            jurisdiction (:jurisdiction (device-unit s device-unit-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:certificate-sequences jurisdiction] (fnil inc 0))
                       (update-in [:device-units device-unit-id] merge device-unit-patch)
                       (update :certificates registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-device-units [s device-units] (when (seq device-units) (swap! a assoc :device-units device-units)) s))

(defn seed-db
  "A MemStore seeded with the demo device-unit set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :eol-screens {} :ledger []
                           :shipment-sequences {} :shipments []
                           :certificate-sequences {} :certificates []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/eol-screen payloads, ledger facts,
  shipment/certificate records) are stored as EDN strings so `langchain.
  db` doesn't expand them into sub-entities -- the same convention
  every sibling actor's store uses."
  {:device-unit/id                {:db/unique :db.unique/identity}
   :verification/device-unit-id   {:db/unique :db.unique/identity}
   :eol-screen/device-unit-id     {:db/unique :db.unique/identity}
   :ledger/seq                    {:db/unique :db.unique/identity}
   :shipment/seq                  {:db/unique :db.unique/identity}
   :certificate/seq               {:db/unique :db.unique/identity}
   :shipment-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :certificate-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- device-unit->tx [{:keys [id device-unit-name rf-power-deviation-actual rf-power-deviation-min rf-power-deviation-max
                                 bonding-press-platen-mass-kg sim-peak-bonding-force-n sim-peak-bonding-pressure-mpa
                                 bonding-pressure-min-mpa bonding-pressure-max-mpa
                                 eol-defect-unresolved? robotics-sim-verified? robotics-sim-record
                                 device-unit-shipped? radio-conformity-certified?
                                 jurisdiction status shipment-number certificate-number]}]
  (cond-> {:device-unit/id id}
    device-unit-name                            (assoc :device-unit/device-unit-name device-unit-name)
    rf-power-deviation-actual                   (assoc :device-unit/rf-power-deviation-actual rf-power-deviation-actual)
    rf-power-deviation-min                      (assoc :device-unit/rf-power-deviation-min rf-power-deviation-min)
    rf-power-deviation-max                      (assoc :device-unit/rf-power-deviation-max rf-power-deviation-max)
    bonding-press-platen-mass-kg                (assoc :device-unit/bonding-press-platen-mass-kg bonding-press-platen-mass-kg)
    sim-peak-bonding-force-n                    (assoc :device-unit/sim-peak-bonding-force-n sim-peak-bonding-force-n)
    (some? sim-peak-bonding-pressure-mpa)       (assoc :device-unit/sim-peak-bonding-pressure-mpa sim-peak-bonding-pressure-mpa)
    bonding-pressure-min-mpa                    (assoc :device-unit/bonding-pressure-min-mpa bonding-pressure-min-mpa)
    bonding-pressure-max-mpa                    (assoc :device-unit/bonding-pressure-max-mpa bonding-pressure-max-mpa)
    (some? eol-defect-unresolved?)              (assoc :device-unit/eol-defect-unresolved? eol-defect-unresolved?)
    (some? robotics-sim-verified?)              (assoc :device-unit/robotics-sim-verified? robotics-sim-verified?)
    (some? robotics-sim-record)                 (assoc :device-unit/robotics-sim-record (enc robotics-sim-record))
    (some? device-unit-shipped?)                (assoc :device-unit/device-unit-shipped? device-unit-shipped?)
    (some? radio-conformity-certified?)         (assoc :device-unit/radio-conformity-certified? radio-conformity-certified?)
    jurisdiction                                (assoc :device-unit/jurisdiction jurisdiction)
    status                                      (assoc :device-unit/status status)
    shipment-number                             (assoc :device-unit/shipment-number shipment-number)
    certificate-number                          (assoc :device-unit/certificate-number certificate-number)))

(def ^:private device-unit-pull
  [:device-unit/id :device-unit/device-unit-name :device-unit/rf-power-deviation-actual
   :device-unit/rf-power-deviation-min :device-unit/rf-power-deviation-max
   :device-unit/bonding-press-platen-mass-kg :device-unit/sim-peak-bonding-force-n :device-unit/sim-peak-bonding-pressure-mpa
   :device-unit/bonding-pressure-min-mpa :device-unit/bonding-pressure-max-mpa
   :device-unit/eol-defect-unresolved? :device-unit/robotics-sim-verified? :device-unit/robotics-sim-record
   :device-unit/device-unit-shipped? :device-unit/radio-conformity-certified?
   :device-unit/jurisdiction :device-unit/status :device-unit/shipment-number :device-unit/certificate-number])

(defn- pull->device-unit [m]
  (when (:device-unit/id m)
    {:id (:device-unit/id m) :device-unit-name (:device-unit/device-unit-name m)
     :rf-power-deviation-actual (:device-unit/rf-power-deviation-actual m)
     :rf-power-deviation-min (:device-unit/rf-power-deviation-min m)
     :rf-power-deviation-max (:device-unit/rf-power-deviation-max m)
     :bonding-press-platen-mass-kg (:device-unit/bonding-press-platen-mass-kg m)
     :sim-peak-bonding-force-n (:device-unit/sim-peak-bonding-force-n m)
     :sim-peak-bonding-pressure-mpa (:device-unit/sim-peak-bonding-pressure-mpa m)
     :bonding-pressure-min-mpa (:device-unit/bonding-pressure-min-mpa m)
     :bonding-pressure-max-mpa (:device-unit/bonding-pressure-max-mpa m)
     :eol-defect-unresolved? (boolean (:device-unit/eol-defect-unresolved? m))
     :robotics-sim-verified? (boolean (:device-unit/robotics-sim-verified? m))
     :robotics-sim-record (dec* (:device-unit/robotics-sim-record m))
     :device-unit-shipped? (boolean (:device-unit/device-unit-shipped? m))
     :radio-conformity-certified? (boolean (:device-unit/radio-conformity-certified? m))
     :jurisdiction (:device-unit/jurisdiction m) :status (:device-unit/status m)
     :shipment-number (:device-unit/shipment-number m) :certificate-number (:device-unit/certificate-number m)}))

(defrecord DatomicStore [conn]
  Store
  (device-unit [_ id]
    (pull->device-unit (d/pull (d/db conn) device-unit-pull [:device-unit/id id])))
  (all-device-units [_]
    (->> (d/q '[:find [?id ...] :where [?e :device-unit/id ?id]] (d/db conn))
         (map #(pull->device-unit (d/pull (d/db conn) device-unit-pull [:device-unit/id %])))
         (sort-by :id)))
  (eol-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?k :eol-screen/device-unit-id ?aid] [?k :eol-screen/payload ?p]]
              (d/db conn) id)))
  (requirements-verification-of [_ device-unit-id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?a :verification/device-unit-id ?aid] [?a :verification/payload ?p]]
              (d/db conn) device-unit-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (shipment-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :shipment/seq ?s] [?e :shipment/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (certificate-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :certificate/seq ?s] [?e :certificate/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-shipment-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :shipment-sequence/jurisdiction ?j] [?e :shipment-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-certificate-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :certificate-sequence/jurisdiction ?j] [?e :certificate-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (device-unit-already-shipped? [s device-unit-id]
    (boolean (:device-unit-shipped? (device-unit s device-unit-id))))
  (device-unit-already-certified? [s device-unit-id]
    (boolean (:radio-conformity-certified? (device-unit s device-unit-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :device-unit/upsert
      (d/transact! conn [(device-unit->tx value)])

      :verification/set
      (d/transact! conn [{:verification/device-unit-id (first path) :verification/payload (enc payload)}])

      :eol-screen/set
      (d/transact! conn [{:eol-screen/device-unit-id (first path) :eol-screen/payload (enc payload)}])

      :device-unit/mark-shipped
      (let [device-unit-id (first path)
            {:keys [result device-unit-patch]} (ship-device-unit! s device-unit-id)
            jurisdiction (:jurisdiction (device-unit s device-unit-id))
            next-n (inc (next-shipment-sequence s jurisdiction))]
        (d/transact! conn
                     [(device-unit->tx (assoc device-unit-patch :id device-unit-id))
                      {:shipment-sequence/jurisdiction jurisdiction :shipment-sequence/next next-n}
                      {:shipment/seq (count (shipment-history s)) :shipment/record (enc (get result "record"))}])
        result)

      :device-unit/mark-certified
      (let [device-unit-id (first path)
            {:keys [result device-unit-patch]} (issue-radio-conformity-certificate! s device-unit-id)
            jurisdiction (:jurisdiction (device-unit s device-unit-id))
            next-n (inc (next-certificate-sequence s jurisdiction))]
        (d/transact! conn
                     [(device-unit->tx (assoc device-unit-patch :id device-unit-id))
                      {:certificate-sequence/jurisdiction jurisdiction :certificate-sequence/next next-n}
                      {:certificate/seq (count (certificate-history s)) :certificate/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-device-units [s device-units]
    (when (seq device-units) (d/transact! conn (mapv device-unit->tx (vals device-units)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:device-units ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [device-units]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-device-units s device-units))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo device-unit set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
