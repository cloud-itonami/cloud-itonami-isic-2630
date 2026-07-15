(ns commsdevice.registry
  "Pure-function device-unit-shipment + radio-conformity-certificate
  record construction -- an append-only communication-equipment-
  manufacturer book-of-record draft.

  Like every sibling actor's registry, there is no single international
  check-digit standard for a device-unit-shipment or radio-conformity-
  certificate reference number -- every manufacturer/jurisdiction
  assigns its own reference format. This namespace does NOT invent one;
  it builds a jurisdiction-scoped sequence number and validates the
  record's required fields, the same honest, non-fabricating discipline
  `commsdevice.facts` uses.

  `device-rf-power-out-of-range?` continues this fleet's two-sided
  range check family (`testlab.registry/within-tolerance?` established
  the first, `conservation.registry/body-condition-out-of-range?` the
  second, `water.registry/contaminant-level-out-of-range?` the third,
  `steelworks.registry/heat-chemistry-out-of-range?`/`turbine.registry/
  unit-tolerance-out-of-range?`/`automotive.registry/vehicle-emissions-
  out-of-range?`/`autoparts.registry/part-lot-dppm-out-of-range?`
  further siblings), applying the SAME lo/hi bounds-comparison shape to
  a device-unit's own measured RF conducted-transmit-power deviation
  (dB, from its own certified/rated nominal output power -- a metric
  genuinely specific to a RADIO transmitter, unlike isic-2620's general
  IT-equipment fields) against the device-unit's own recorded
  production-test tolerance band.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real plant/MES control system. It builds the RECORD a
  manufacturer would keep, not the act of shipping the device-unit robot
  action or issuing the radio-conformity certificate itself (that is
  `commsdevice.operation`'s `:actuation/ship-device-unit`/`:actuation/
  issue-radio-conformity-certificate`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  manufacturer's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn device-rf-power-out-of-range?
  "Does `device-unit`'s own `:rf-power-deviation-actual` (dB, conducted
  transmit-power deviation from the device's own certified/rated
  nominal output power) fall outside its own `[:rf-power-deviation-min
  :rf-power-deviation-max]` recorded production-test tolerance band? A
  pure ground-truth check against the device-unit's own permanent
  fields -- no upstream comparison needed. This fleet's ground-truth
  range check family, applied here to the one metric genuinely specific
  to a RADIO transmitter (as opposed to isic-2620's general IT-
  equipment EMC fields)."
  [{:keys [rf-power-deviation-actual rf-power-deviation-min rf-power-deviation-max]}]
  (and (number? rf-power-deviation-actual) (number? rf-power-deviation-min) (number? rf-power-deviation-max)
       (or (< rf-power-deviation-actual rf-power-deviation-min)
           (> rf-power-deviation-actual rf-power-deviation-max))))

(defn register-device-unit-shipment
  "Validate + construct the DEVICE-UNIT-SHIPMENT registration DRAFT --
  the manufacturer's own act of dispatching a real robot handling/
  packout action to release a device-unit for shipment. Pure function
  -- does not touch any real plant/MES control system; it builds the
  RECORD a manufacturer would keep. `commsdevice.governor` independently
  re-verifies the device-unit's own RF-power-deviation sufficiency
  against its own production-test tolerance band, and a double-shipment
  for the same device-unit, before this is ever allowed to commit."
  [device-unit-id jurisdiction sequence]
  (when-not (and device-unit-id (not= device-unit-id ""))
    (throw (ex-info "device-unit-shipment: device_unit_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "device-unit-shipment: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "device-unit-shipment: sequence must be >= 0" {})))
  (let [shipment-number (str (str/upper-case jurisdiction) "-SHP-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "device-unit-shipment-draft"
                "device_unit_id" device-unit-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "DeviceUnitShipment" shipment-number shipment-number)}))

(defn register-radio-conformity-certificate
  "Validate + construct the RADIO-CONFORMITY-CERTIFICATE registration
  DRAFT -- the manufacturer's own act of issuing a real Giteki/RED/FCC-
  style radio-type-approval conformity certificate for a device-unit
  (distinct from isic-2620's general Declaration-of-Conformity: this
  attests RADIO EQUIPMENT type-approval, not just general EMC/product
  safety). Pure function -- does not touch any real plant/MES control
  system or type-approval authority portal; it builds the RECORD a
  manufacturer would keep. `commsdevice.governor` independently
  re-verifies the device-unit's own end-of-line defect resolution
  status, and a double-issuance for the same device-unit, before this
  is ever allowed to commit."
  [device-unit-id jurisdiction sequence]
  (when-not (and device-unit-id (not= device-unit-id ""))
    (throw (ex-info "radio-conformity-certificate: device_unit_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "radio-conformity-certificate: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "radio-conformity-certificate: sequence must be >= 0" {})))
  (let [certificate-number (str (str/upper-case jurisdiction) "-RCC-" (zero-pad sequence 6))
        record {"record_id" certificate-number
                "kind" "radio-conformity-certificate-draft"
                "device_unit_id" device-unit-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "certificate_number" certificate-number
     "certificate" (unsigned-certificate "RadioConformityCertificate" certificate-number certificate-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
