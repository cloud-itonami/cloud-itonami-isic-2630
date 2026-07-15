(ns commsdevice.governor
  "Radio-Compliance Governor -- the independent compliance layer that
  earns the Device Advisor the right to commit. The LLM has no notion
  of radio-type-approval law, whether a device-unit's own measured RF
  conducted-transmit-power deviation actually stays within its own
  recorded production-test bounds, whether a real `physics-2d`-
  simulated display-bonding press reading actually clears its own
  recorded acceptance band, whether an end-of-line-detected defect
  against the device-unit has actually stayed unresolved, or when an
  act stops being a draft and becomes a real-world robot device-unit
  shipment or radio-conformity-certificate issuance, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD --
  the communication-equipment-manufacturer analog of
  `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Seven checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated radio-type-approval spec-basis, incomplete evidence, a
  robot display-bonding-press simulation that never ran or that
  independently re-checks out-of-tolerance, an out-of-spec RF conducted-
  power deviation, an unresolved end-of-line defect, or a double
  shipment/certificate-issuance). The confidence/actuation gate is SOFT:
  it asks a human to look (low confidence / actuation), and the human
  may approve -- but see `commsdevice.phase`: for `:stake :actuation/
  ship-device-unit`/`:actuation/issue-radio-conformity-certificate` (a
  real safety-/compliance-critical act) NO phase ever allows auto-
  commit either. Two independent layers agree that actuation is always
  a human call.

    1. Spec-basis                  -- did the radio-compliance-rules
                                       proposal cite an OFFICIAL source
                                       (`commsdevice.facts`), or invent
                                       one?
    2. Evidence incomplete         -- for `:actuation/ship-device-unit`/
                                       `:actuation/issue-radio-
                                       conformity-certificate`, has the
                                       device-unit actually been
                                       verified with a full Giteki/RED/
                                       FCC-style radio-type-approval
                                       evidence checklist (RF-
                                       conformance-test-report/SAR-test-
                                       report/IEC-62368-1-safety-test-
                                       report/etc.) on file?
    3. Robot simulation missing or
       independently out-of-
       tolerance                    -- for `:actuation/ship-device-
                                       unit`, has the robot display-
                                       bonding-press verification
                                       mission (`commsdevice.robotics`)
                                       actually run and been recorded
                                       on the device-unit
                                       (`:robotics-sim-verified?`)? AND
                                       INDEPENDENTLY recompute whether
                                       the device-unit's own recorded
                                       REAL `physics-2d`-simulated
                                       display-bonding-press reading
                                       (`:sim-peak-bonding-pressure-
                                       mpa`) falls outside its own
                                       recorded bonding-pressure
                                       acceptance band
                                       (`commsdevice.robotics/
                                       simulation-out-of-tolerance?`),
                                       ignoring whatever :passed?
                                       verdict the mission run itself
                                       stored -- the same 'ground
                                       truth, not self-report'
                                       discipline check 4 below uses
                                       for RF power deviation.
    4. Device-unit RF power out of
       range                         -- for `:actuation/ship-device-
                                       unit`, INDEPENDENTLY recompute
                                       whether the device-unit's own
                                       measured RF conducted-transmit-
                                       power deviation falls outside
                                       its own recorded production-test
                                       tolerance band (`commsdevice.
                                       registry/device-rf-power-out-of-
                                       range?`) -- needs no proposal
                                       inspection or stored-verdict
                                       lookup at all. One of this
                                       fleet's two-sided range check
                                       family (`testlab.governor/
                                       within-tolerance-violations`/
                                       `conservation.governor/body-
                                       condition-out-of-range-
                                       violations`/`water.governor/
                                       contaminant-level-out-of-range-
                                       violations`/`steelworks.
                                       governor`/`turbine.governor`/
                                       `automotive.governor`/`autoparts.
                                       governor` established the
                                       priors; `commsdevice.robotics/
                                       bonding-pressure-out-of-range?`
                                       above is a further sibling).
    5. End-of-line defect unresolved -- reported by THIS proposal itself
                                       (an `:end-of-line-quality/
                                       screen` that just found an
                                       unresolved defect), or
                                       already on file for the
                                       device-unit (`:end-of-line-
                                       quality/screen`/`:actuation/
                                       issue-radio-conformity-
                                       certificate`). Evaluated
                                       UNCONDITIONALLY (not scoped to
                                       a specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/
                                       `automotive.governor/end-of-
                                       line-defect-unresolved-
                                       violations`/... (prior
                                       siblings)... established --
                                       exercised in tests/demo via
                                       `:end-of-line-quality/screen`
                                       DIRECTLY, not via an actuation
                                       op against an unscreened
                                       device-unit -- see this ns's own
                                       test suite.
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/ship-
                                       device-unit`/`:actuation/issue-
                                       radio-conformity-certificate`
                                       (REAL safety-/compliance-
                                       critical acts) -> escalate.

  Two more guards, double-shipment/double-certificate-issuance
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-shipped-violations`/`already-certified-violations` refuse
  to ship a device-unit action/issue a radio-conformity certificate for
  the SAME device-unit twice, off dedicated `:device-unit-shipped?`/
  `:radio-conformity-certified?` facts (never a `:status` value) -- the
  SAME 'check a dedicated boolean, not status' discipline every prior
  sibling governor's guards establish, informed by
  `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [commsdevice.facts :as facts]
            [commsdevice.registry :as registry]
            [commsdevice.robotics :as robotics]
            [commsdevice.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Shipping a real device-unit action and issuing a real radio-
  conformity certificate are the two real-world actuation events this
  actor performs -- a two-member set, matching every prior dual-
  actuation sibling's shape."
  #{:actuation/ship-device-unit :actuation/issue-radio-conformity-certificate})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:radio-compliance-rules/verify` (or actuation) proposal with no
  spec-basis citation is a HARD violation -- never invent a
  jurisdiction's radio-type-approval requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:radio-compliance-rules/verify :actuation/ship-device-unit :actuation/issue-radio-conformity-certificate} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は無線設備技術基準適合要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/ship-device-unit`/`:actuation/issue-radio-
  conformity-certificate`, the jurisdiction's required radio-type-
  approval evidence (RF-conformance-test-report/SAR-test-report/IEC-
  62368-1-safety-test-report/etc.) must actually be satisfied -- do not
  trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/ship-device-unit :actuation/issue-radio-conformity-certificate} op)
    (let [a (store/device-unit st subject)
          verification (store/requirements-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction a) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要無線技術基準適合証明書類(RF試験報告書/SAR試験報告書/IEC-62368-1安全性試験報告書等)が充足していない状態での提案"}]))))

(defn- robotics-simulation-violations
  "For `:actuation/ship-device-unit`: HARD hold if the robot display-
  bonding-press verification mission (`commsdevice.robotics`) never ran
  and was recorded on the device-unit (`:robotics-sim-verified?`), OR
  if it did but an INDEPENDENT recompute of the device-unit's own REAL
  `physics-2d`-simulated display-bonding-press telemetry (`:sim-peak-
  bonding-pressure-mpa`) falls outside its own recorded bonding-
  pressure acceptance band (`commsdevice.robotics/simulation-out-of-
  tolerance?`) -- never trusts the mission's own stored :passed?
  verdict alone, the same discipline `device-rf-power-out-of-range-
  violations` below uses for RF power deviation."
  [{:keys [op subject]} st]
  (when (= op :actuation/ship-device-unit)
    (let [a (store/device-unit st subject)]
      (cond
        (not (:robotics-sim-verified? a))
        [{:rule :robotics-simulation-missing
          :detail (str subject " のディスプレイ光学接着(OCAラミネート)プレス検証ミッションが未実行・未合格")}]

        (robotics/simulation-out-of-tolerance? a)
        [{:rule :robotics-simulation-out-of-tolerance
          :detail (str subject " の実測ボンディング圧力(" (:sim-peak-bonding-pressure-mpa a)
                       "MPa)が独立再検証で許容範囲[" robotics/min-bonding-pressure-mpa ","
                       robotics/max-bonding-pressure-mpa "]MPaを逸脱")}]))))

(defn- device-rf-power-out-of-range-violations
  "For `:actuation/ship-device-unit`, INDEPENDENTLY recompute whether
  the device-unit's own RF conducted-transmit-power deviation falls
  outside its own recorded production-test tolerance band via
  `commsdevice.registry/device-rf-power-out-of-range?` -- needs no
  proposal inspection or stored-verdict lookup at all, since its
  inputs are permanent ground-truth fields already on the device-unit."
  [{:keys [op subject]} st]
  (when (= op :actuation/ship-device-unit)
    (let [a (store/device-unit st subject)]
      (when (registry/device-rf-power-out-of-range? a)
        [{:rule :device-rf-power-out-of-range
          :detail (str subject " の実測RF送信電力偏差(" (:rf-power-deviation-actual a)
                      "dB)が仕様範囲[" (:rf-power-deviation-min a) "," (:rf-power-deviation-max a) "]dBを逸脱")}]))))

(defn- end-of-line-defect-unresolved-violations
  "An unresolved end-of-line-detected defect -- reported by THIS
  proposal (e.g. an `:end-of-line-quality/screen` that itself just
  found one), or already on file in the store for the device-unit
  (`:end-of-line-quality/screen`/`:actuation/issue-radio-conformity-
  certificate`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        device-unit-id (when (contains? #{:end-of-line-quality/screen :actuation/issue-radio-conformity-certificate} op) subject)
        hit-on-file? (and device-unit-id (= :unresolved (:verdict (store/eol-screen-of st device-unit-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :end-of-line-defect-unresolved
        :detail "未解決の完成検査欠陥がある状態での無線設備適合証明書発行提案は進められない"}])))

(defn- already-shipped-violations
  "For `:actuation/ship-device-unit`, refuses to ship a device-unit
  action for the SAME device-unit twice, off a dedicated `:device-
  unit-shipped?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/ship-device-unit)
    (when (store/device-unit-already-shipped? st subject)
      [{:rule :already-shipped
        :detail (str subject " は既に出荷実行済み")}])))

(defn- already-certified-violations
  "For `:actuation/issue-radio-conformity-certificate`, refuses to
  issue a radio-conformity certificate for the SAME device-unit twice,
  off a dedicated `:radio-conformity-certified?` fact (never a
  `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/issue-radio-conformity-certificate)
    (when (store/device-unit-already-certified? st subject)
      [{:rule :already-certified
        :detail (str subject " は既に無線設備適合証明書発行済み")}])))

(defn check
  "Censors a Device Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (robotics-simulation-violations request st)
                           (device-rf-power-out-of-range-violations request st)
                           (end-of-line-defect-unresolved-violations request proposal st)
                           (already-shipped-violations request st)
                           (already-certified-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
