(ns commsdevice.robotics
  "Robot-executed display-module verification -- the concrete, actor-
  level realization of ADR-2607011000's robotics premise (every
  cloud-itonami vertical is designed on the premise that a robot
  performs the physical-domain work; an independent governor gates any
  action before it ever reaches hardware), replicating the pattern
  ADR-2607142800 established as the fleet-wide convention and
  ADR-2607151600/ADR-2607152000 upgraded to a REAL time-stepped physics
  simulation (reference implementations: `automotive.robotics` in
  `cloud-itonami-isic-2910`, `autoparts.robotics` in
  `cloud-itonami-isic-2930`, `cementmill.robotics` in
  `cloud-itonami-isic-2394`) for THIS actor's own manufacturing-process
  evidence requirement: a device-unit-shipment proposal must cite a
  real display-module optical-bonding (OCA lamination) press-run report
  actually on file -- not merely a self-reported checklist string.

  The display-module optical-bonding step of the mission is an ACTUAL
  time-stepped `kotoba-lang/physics-2d` rigid-body simulation -- a real
  press-platen `Body2D` closes at a controlled velocity onto a real
  static (mass 0) display-stack `Body2D` (cover glass + Optical Clear
  Adhesive [OCA] layer + LCD/OLED module, the real lamination sandwich a
  smartphone display-bonding press produces), `world-step` actually
  integrates/collides/resolves the contact over real ticks, and
  `:sim-peak-bonding-force-n`/`:sim-peak-bonding-pressure-mpa` are read
  directly off the ACTUAL simulated velocity trajectory (`bonding-
  telemetry-for` below) -- not invented. This vertical has no design-
  library sibling repo, so the physics module lives DIRECTLY in this ns
  and takes a real pinned git-coordinate dependency on
  `kotoba-lang/physics-2d` alone (see `deps.edn`), mirroring
  `autoparts.robotics`'s/`cementmill.robotics`'s own simplification
  versus the automotive pilot's design-library pairing.

  A robot mission (`kotoba.robotics/mission`) walks the device-unit
  through three steps in the display-bonding cell -- a vision-guided
  display-stack alignment check, the OCA-lamination press-bond itself,
  and a post-bond bond-line defect scan (checking for trapped
  air/Newton rings) -- built with `kotoba.robotics/action` +
  `kotoba.robotics/telemetry-proof`, and reports an overall :passed?
  verdict now derived from the REAL simulated press reading
  (`:sim-peak-bonding-pressure-mpa`, see `bonding-telemetry-for`), not a
  hand-set field. `simulation-out-of-tolerance?` independently
  re-derives that verdict from the device-unit's OWN recorded real
  telemetry cross-checked against the device-unit's OWN recorded
  bonding-pressure acceptance band (`:bonding-pressure-min-mpa`/
  `:bonding-pressure-max-mpa`), never from the mission's self-reported
  result -- the SAME 'ground truth, not self-report' discipline
  `commsdevice.registry/device-rf-power-out-of-range?` uses for RF
  conducted-power deviation. `commsdevice.governor`'s
  `robotics-simulation-violations` calls this ns's independent recheck,
  never the stored :passed? value, before any `:actuation/ship-device-
  unit` proposal may commit.

  Honest scope (mirrors `cementmill.robotics`'s own disclosures,
  ADR-2607151600/ADR-2607152000):

  - 2D projection only (`physics-2d` has no 3D solver) -- x is the
    press's direction of travel (platen closing onto the display
    stack), y is lateral; world gravity is [0 0] (a horizontal press-
    closing projection, not a vertical drop).
  - the display stack is modeled as a STATIC (mass 0) AABB, mirroring
    `cementmill.robotics`'s cube-specimen / `vdesign.simphysics`'s
    immovable crash-barrier pattern: `physics-2d` treats a mass-0 body
    as having zero inverse mass (an immovable anchor), which is also
    physically apt here -- a real OCA-lamination press's display-stack
    fixture/vacuum chuck is clamped to the press bed, not free to
    recoil. `physics-2d` has NO material-stiffness/adhesive-compliance
    model whatsoever, so the OCA layer's own real viscoelastic behavior
    cannot itself vary the simulated reading (the SAME disclosed
    limitation `cementmill.robotics`/`vdesign.simphysics` state) -- what
    DOES vary the reading is this device-unit's own recorded press-run
    configuration (`:bonding-press-platen-mass-kg`, see `bonding-
    telemetry-for`).
  - `press-closing-velocity-mps`/`crush-travel-m` (the OCA layer's own
    real compressible squeeze-travel during lamination bonding) are this
    ns's own disclosed engineering priors, NOT measured facts for any
    specific device-unit -- `physics-2d` has no material-compliance
    model at all, so this namespace cannot derive them from first
    principles; they only need to be SOME disclosed distance/rate pair
    that derives a physically meaningful timestep, exactly the role
    `cementmill.robotics`'s `peak-strain-fraction`/`crush-travel-m` and
    `press-closing-velocity-mps` play there. `press-closing-velocity-
    mps` is a deliberately chosen, disclosed ANALOG closing rate (fast
    enough for `physics-2d`'s single-tick boxcar-collision model to
    produce a physically meaningful impulse), never presented as a
    literal reproduction of a real OCA-lamination production line's
    actual (much slower, continuous, non-shock) press-closing feed
    rate. What IS real: `world-step` actually integrates this velocity
    tick-by-tick, not a shortcut formula.
  - `physics-2d`'s impulse resolver has no progressive adhesive-squeeze
    stiffness/force-deflection model: whatever tick first detects ANY
    AABB overlap fully zeroes the closing velocity in that ONE tick
    (given `restitution` 0) -- a discrete, instantaneous 'boxcar' stop,
    the SAME disclosed limitation `cementmill.robotics`/
    `vdesign.simphysics` state. By exact kinematic identity (a=v^2/d for
    a boxcar full stop over transit distance d at speed v), the peak
    deceleration is INDEPENDENT of the platen's own mass when colliding
    with a mass-0 (immovable) display stack (mass cancels
    algebraically in `physics-2d`'s `resolve-contact`, the SAME
    verified, documented property `cementmill.robotics`/
    `vdesign.simphysics` establish) -- so `:bonding-press-platen-mass-
    kg` is what actually moves `:sim-peak-bonding-force-n`/`:sim-peak-
    bonding-pressure-mpa` here (via F = m*a), never the closing
    velocity or squeeze-travel (both fixed constants, shared by every
    device-unit).
  - `display-bonding-area-mm2` (the cover-glass/module active bonding
    contact area used to convert the simulated force into a pressure
    reading) is a representative modern smartphone display module
    footprint (roughly a 6.1-inch-class handset), not a per-device-unit
    CAD/BREP measurement -- this ns has no design-library bridge, unlike
    automotive's `kami-engine-vehicle-designer` pairing.
  - `bonding-pressure-min-mpa`/`bonding-pressure-max-mpa` (the
    acceptance band this ns's `bonding-pressure-out-of-range?` checks
    against) is a REASONED ENGINEERING ESTIMATE, not a confidently-
    sourced single citation -- the same honest disclosure style the
    retail drop-test ADR used for its 400g ceiling. Real-world OCA
    optical-bonding lamination processes are commonly described (across
    display-manufacturing process literature) as operating in a
    low-tenths-of-an-MPa range for direct press-bonding (distinct from
    higher-pressure/elevated-temperature autoclave debubbling steps,
    out of scope here) -- too little press force risks incomplete
    wetting/trapped air at the OCA interface, too much risks OCA
    squeeze-out past the bond line or cover-glass/module damage. This
    ns's `0.15`-`0.55` MPa band is a plausible order-of-magnitude
    estimate for that failure mode, disclosed honestly as such."
  (:require [kotoba.robotics :as robotics]
            [physics-2d :as p2d]))

;; ───────────────────── real, disclosed physical constants ─────────────────────

(def ^:const module-width-mm
  "Representative modern smartphone display-module width (mm), a
  6.1-inch-class handset footprint -- not a per-device-unit CAD
  measurement (this ns has no BREP/CAD pipeline, unlike automotive's
  design-library bridge)."
  70.0)

(def ^:const module-length-mm
  "Representative modern smartphone display-module length (mm), same
  6.1-inch-class handset footprint as `module-width-mm`."
  150.0)

(def ^:const display-bonding-area-mm2
  "The display module's own loaded/bonded face area (mm^2). 1 MPa = 1
  N/mm^2, so dividing a simulated force (N) by this real, fixed
  geometry constant converts directly to a pressure reading (MPa)
  comparable to the device-unit's own recorded :bonding-pressure-min-
  mpa/:bonding-pressure-max-mpa (both MPa)."
  (* module-width-mm module-length-mm))

(def ^:const oca-layer-thickness-mm
  "Representative Optical Clear Adhesive (OCA) layer thickness (mm) for
  a smartphone cover-glass-to-module optical bond -- a commonly-cited
  order of magnitude for this adhesive class, NOT a measured fact for
  any specific device-unit."
  0.15)

(def ^:const crush-travel-m
  "The OCA layer's own real compressible squeeze-travel (m) during
  press-bonding -- modeled here as the layer's full disclosed thickness
  (a simplification, not a stress-strain derivation: `physics-2d` has
  no material-compliance model at all, so this namespace only needs
  SOME disclosed distance to derive a timestep, exactly the role
  `cementmill.robotics`'s `crush-travel-m` plays there)."
  (/ oca-layer-thickness-mm 1000.0))

(def ^:const press-closing-velocity-mps
  "The press-platen's controlled closing velocity (m/s) for this
  simulation -- see this ns's own docstring for why this is a
  disclosed ANALOG rate (fast enough for `physics-2d`'s boxcar-collision
  model to produce a meaningful impulse), not a literal reproduction of
  a real OCA-lamination production line's actual continuous,
  non-shock feed rate."
  0.15)

(def ^:const dt
  "Per-tick timestep (s) -- derived from THIS simulation's own
  crush-travel/closing-velocity (the nominal transit time across the
  OCA layer's own squeeze zone), the SAME principled-not-arbitrary
  identity `cementmill.robotics`/`vdesign.simphysics` use for their own
  `dt`."
  (/ crush-travel-m press-closing-velocity-mps))

(def ^:const platen-half-w-m
  "Press-platen AABB half-width (m) along the travel axis -- a thin,
  rigid platen face (10 mm full thickness); `physics-2d` colliders do
  not deform, so this dimension is a disclosed, arbitrary rigid-body
  stand-in, not a load-bearing physical parameter."
  0.005)

(def ^:const platen-half-h-m
  "Press-platen AABB half-height (m), lateral -- 80 mm full width,
  wider than the 70 mm display module so the WHOLE module face loads,
  matching how a real optical-bonding press's platen/vacuum head is
  sized >= the display module."
  0.04)

(def ^:const display-stack-half-w-m
  "Display-stack AABB half-width (m) along the travel axis -- the real
  bonded stack's (cover glass + OCA + LCD/OLED module) own half-
  thickness, from a representative 1.5 mm total stack thickness."
  (/ (/ 1.5 1000.0) 2.0))

(def ^:const display-stack-half-h-m
  "Display-stack AABB half-height (m), lateral -- the module's own real
  half-width."
  (/ (/ module-width-mm 1000.0) 2.0))

(def ^:const gap-m
  "Press standoff distance (m) the platen starts behind the display
  stack, so the trajectory captures a real pre-contact approach phase,
  not just the collision tick itself (mirrors `cementmill.robotics`'s
  `gap-m`)."
  0.01)

(def ^:const settle-ticks
  "Extra ticks appended after the platen is expected to reach the
  display stack, so the trajectory also captures post-contact settling
  -- the SAME constant + rationale as `cementmill.robotics`/
  `vdesign.simphysics`: `physics-2d`'s positional correction removes 80%
  of any remaining overlap per tick, so residual overlap after 15 more
  ticks is ~3e-11 of whatever it was at first contact."
  15)

(def ^:const min-bonding-pressure-mpa
  "Real, disclosed minimum acceptable OCA-lamination bonding pressure
  (MPa) -- see ns docstring for the reasoned-engineering-estimate
  disclosure. Too little press force risks incomplete adhesive wetting
  and trapped air at the bond interface."
  0.15)

(def ^:const max-bonding-pressure-mpa
  "Real, disclosed maximum acceptable OCA-lamination bonding pressure
  (MPa) -- see ns docstring for the reasoned-engineering-estimate
  disclosure. Too much press force risks OCA squeeze-out past the bond
  line or cover-glass/module damage."
  0.55)

;; ------------------------------ real simulation ------------------------------

(defn simulate-bonding-press
  "Time-steps a REAL `physics-2d` world for ONE display-module optical-
  bonding (OCA lamination) press cycle: a press-platen `Body2D` (mass
  `platen-mass-kg`, velocity `press-closing-velocity-mps`) approaches
  and collides with a static (mass 0, immovable -- matching
  `cementmill.robotics`'s cube-specimen pattern) display-stack `Body2D`.
  Returns {:trajectory [{:tick :position :velocity} ...] (platen body
  only) :sim-peak-bonding-force-n n :sim-peak-bonding-pressure-mpa n
  :sim-peak-bond-travel-m n :ticks n :dt n :closing-velocity-mps n}.

  `:sim-peak-bonding-force-n` is `platen-mass-kg` times the PEAK
  magnitude of tick-to-tick velocity change (along the travel axis)
  divided by `dt` -- F = m*a, derived from the ACTUAL simulated
  velocity trajectory (the SAME technique `cementmill.robotics`/
  `vdesign.simphysics` use). `:sim-peak-bonding-pressure-mpa` divides
  that force by the display module's own real bonded face area (mm^2)
  -- 1 MPa = 1 N/mm^2 -- so it is directly comparable to a device-
  unit's own recorded :bonding-pressure-min-mpa/:bonding-pressure-max-
  mpa (both MPa). `:sim-peak-bond-travel-m` is the largest AABB
  penetration depth (m) actually observed between the platen's leading
  face and the display stack's near face across the whole trajectory
  -- informational, derived from the actual simulated positions, not
  invented.

  Pure, deterministic -- the same `platen-mass-kg` always reproduces
  the same telemetry; no IO, no wall-clock."
  [platen-mass-kg]
  (let [v0 press-closing-velocity-mps
        approach-m (+ gap-m platen-half-w-m display-stack-half-w-m)
        ticks (long (+ settle-ticks (long (Math/ceil (/ approach-m (* v0 dt))))))
        stack-x 0.0
        platen-x (- stack-x display-stack-half-w-m platen-half-w-m gap-m)
        platen (p2d/make-body {:position [platen-x 0.0]
                                :velocity [v0 0.0]
                                :mass platen-mass-kg
                                :restitution 0.0
                                :friction 0.0
                                :collider (p2d/make-aabb-collider platen-half-w-m platen-half-h-m)
                                :user-data :platen})
        display-stack (p2d/make-body {:position [stack-x 0.0]
                                       :velocity [0.0 0.0]
                                       :mass 0.0
                                       :restitution 0.0
                                       :friction 0.0
                                       :collider (p2d/make-aabb-collider display-stack-half-w-m display-stack-half-h-m)
                                       :user-data :display-stack})
        w0 (p2d/world-new [0.0 0.0])
        [w1 pid] (p2d/world-add w0 platen)
        [w2 _sid] (p2d/world-add w1 display-stack)
        worlds (reductions (fn [w _] (p2d/world-step w dt)) w2 (range ticks))
        trajectory (mapv (fn [tick world]
                            (let [b (nth (:bodies world) pid)]
                              {:tick tick :position (:position b) :velocity (:velocity b)}))
                          (range (count worlds)) worlds)
        vxs (mapv (comp first :velocity) trajectory)
        peak-decel-mps2 (->> (map (fn [va vb] (Math/abs (/ (- vb va) dt))) vxs (rest vxs))
                              (reduce max 0.0))
        contact-plane-x (- stack-x display-stack-half-w-m)
        penetrations-m (mapv (fn [{:keys [position]}]
                                (max 0.0 (- (+ (first position) platen-half-w-m) contact-plane-x)))
                              trajectory)
        peak-force-n (* platen-mass-kg peak-decel-mps2)]
    {:trajectory trajectory
     :sim-peak-bonding-force-n peak-force-n
     :sim-peak-bonding-pressure-mpa (/ peak-force-n display-bonding-area-mm2)
     :sim-peak-bond-travel-m (reduce max 0.0 penetrations-m)
     :ticks (count trajectory)
     :dt dt
     :closing-velocity-mps v0}))

(defn bonding-telemetry-for
  "Runs the REAL `simulate-bonding-press` time-stepped `physics-2d`
  simulation for `device-unit`'s own recorded `:bonding-press-platen-
  mass-kg` press-run configuration and returns the actual simulated
  telemetry: {:sim-peak-bonding-force-n n :sim-peak-bonding-pressure-
  mpa n :sim-peak-bond-travel-m n :ticks n :dt n :closing-velocity-mps
  n}. Pure, deterministic -- no IO; the same `:bonding-press-platen-
  mass-kg` always reproduces the same telemetry."
  [device-unit]
  (simulate-bonding-press (:bonding-press-platen-mass-kg device-unit)))

(def mission-actions
  "The three-step display-bonding verification mission every device-unit
  walks through before `:actuation/ship-device-unit` is proposable.
  :sense at :none safety, :actuate at :low -- verification/QA handling
  of a stationary display module, not the moving-shipment actuation
  that is `:actuation/ship-device-unit` itself (always :safety-critical
  -- see `commsdevice.governor`)."
  [{:step :display-stack-alignment-check :kind :sense   :safety :none}
   {:step :oca-lamination-press-bond     :kind :actuate :safety :low}
   {:step :bond-line-defect-scan         :kind :sense   :safety :none}])

(defn bonding-pressure-out-of-range?
  "Ground-truth check: does `device-unit`'s own recorded REAL
  `:sim-peak-bonding-pressure-mpa` (the ACTUAL `physics-2d`-simulated
  press-collision reading -- see `bonding-telemetry-for`) fall outside
  its own recorded [:bonding-pressure-min-mpa :bonding-pressure-max-
  mpa] acceptance-band bounds? Reuses the device-unit's OWN already-
  established real acceptance band constants (`min-bonding-pressure-
  mpa`/`max-bonding-pressure-mpa` above). Needs no mission run or
  proposal inspection once the telemetry is on file -- its inputs are
  permanent fields already on the device-unit, the same shape
  `commsdevice.registry/device-rf-power-out-of-range?` uses for RF
  power deviation."
  [{:keys [sim-peak-bonding-pressure-mpa bonding-pressure-min-mpa bonding-pressure-max-mpa]}]
  (and (number? sim-peak-bonding-pressure-mpa) (number? bonding-pressure-min-mpa) (number? bonding-pressure-max-mpa)
       (or (< sim-peak-bonding-pressure-mpa bonding-pressure-min-mpa)
           (> sim-peak-bonding-pressure-mpa bonding-pressure-max-mpa))))

(defn simulate-bonding-cell
  "Run the robot display-bonding verification mission for `device-unit-
  id` (`device-unit` is the full device-unit record, incl.
  `:bonding-press-platen-mass-kg` and `:bonding-pressure-min-mpa`/
  `:bonding-pressure-max-mpa`). Actually runs the REAL engine:
  `bonding-telemetry-for` -- the actual `physics-2d`-stepped press-
  platen/display-stack collision trajectory (`:sim-peak-bonding-force-
  n`/`:sim-peak-bonding-pressure-mpa`).

  Returns {:mission .. :actions [{:action .. :proof ..} ..] :passed?
  bool :sim-peak-bonding-force-n n :sim-peak-bonding-pressure-mpa n}.
  Deterministic: :passed? is derived from the device-unit's OWN
  recorded press-run configuration via the REAL simulated trajectory
  (`bonding-pressure-out-of-range?`), never invented or randomized --
  `kotoba.robotics` mandates no network/IO, and a repeatable simulation
  is what makes the governor's independent recheck
  (`simulation-out-of-tolerance?`) meaningful."
  [device-unit-id device-unit]
  (let [telemetry (bonding-telemetry-for device-unit)
        out-of-range? (bonding-pressure-out-of-range? (merge device-unit telemetry))
        reading (if out-of-range? :out-of-tolerance :nominal)
        mission (robotics/mission (str "mission-" device-unit-id "-display-bonding")
                                   :robot/display-bonding-cell-1
                                   :display-bonding-verification
                                   :boundaries {:station "display-module-oca-lamination-cell"}
                                   :max-steps (count mission-actions))
        actions (mapv (fn [{:keys [step kind safety]}]
                        (let [a (robotics/action (str (:mission/id mission) "-" (name step))
                                                  (:mission/id mission) kind safety
                                                  :params {:step step :device-unit-id device-unit-id})]
                          {:action a
                           :proof (robotics/telemetry-proof (:mission/id mission) step reading
                                                             :provenance :simulated)}))
                      mission-actions)]
    {:mission mission
     :actions actions
     :passed? (not out-of-range?)
     :sim-peak-bonding-force-n (:sim-peak-bonding-force-n telemetry)
     :sim-peak-bonding-pressure-mpa (:sim-peak-bonding-pressure-mpa telemetry)}))

(defn simulation-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does `device-
  unit`'s OWN current, on-file REAL simulated press telemetry
  (`:sim-peak-bonding-pressure-mpa`) fall out of its own recorded
  bonding-pressure acceptance band right now? Ignores whatever :passed?
  verdict a prior mission run stored -- identical in spirit to
  `commsdevice.registry/device-rf-power-out-of-range?`'s refusal to
  trust a proposal's self-report. Does NOT re-run the simulation -- it
  re-derives the boolean from the real, already-persisted telemetry
  field (`commsdevice.store` persists it on every `:device-unit/
  upsert`), the same 'ground truth, not self-report' discipline applied
  to the STORED reading, not a fresh recompute."
  [device-unit]
  (bonding-pressure-out-of-range? device-unit))
