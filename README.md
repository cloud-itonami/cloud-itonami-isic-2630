# cloud-itonami-isic-2630

Open Business Blueprint for **ISIC Rev.5 2630**: manufacture of
communication equipment -- smartphone/handset final-assembly, radio-
type-approval evidence verification, end-of-line quality screening, a
REAL time-stepped physics simulation of the display-module optical-
bonding (OCA lamination) press, robot display-bonding-cell verification
and radio-conformity-certificate finalization for a community
communication-equipment plant.

This repository publishes a communication-equipment-manufacturing
actor -- device-unit intake, per-jurisdiction radio-type-approval
(Giteki/RED/FCC) evidence-checklist verification, end-of-line-defect
screening, robot display-bonding-press verification and radio-
conformity-certificate issuance -- as an OSS business that any
qualified smartphone/handset assembly plant can fork, deploy, run,
improve and sell, so a plant keeps its own build and radio-compliance
history instead of renting a closed MES / quality SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **Device Advisor ⊣
Radio-Compliance Governor**.

## Scope note: a RADIO transmitter, not general IT/EMC equipment

This repository is scoped to **communication equipment** -- devices
whose defining feature is an actual cellular/Wi-Fi/Bluetooth RADIO
TRANSMITTER (smartphones/handsets), not general computers/peripherals.
Distinct from:

- `cloud-itonami-isic-2620` -- manufacture of computers and peripheral
  equipment (laptops, desktops, servers, monitors). Most isic-2620
  devices have NO intentional radio transmitter, so that actor's
  compliance catalog (`deviceassembly.facts`) is a general EMC/
  product-safety self-declaration regime: VCCI/PSE (JPN), FCC Part 15
  Subpart B unintentional-radiator SDoC (USA), UKCA EMC (GBR), CE EMC
  Directive + RoHS (DEU). THIS repository's device-units DO have a real
  radio transmitter, so `commsdevice.facts` requires a materially
  stricter, radio-specific regime instead: Japan's 技術基準適合証明
  (Giteki mark, MIC/総務省), the EU Radio Equipment Directive
  2014/53/EU (RED), US FCC Part 15 Subpart C (intentional radiators) +
  Part 22/24/27 (licensed cellular services) Certification, and the UK
  Radio Equipment Regulations 2017 -- plus, uniquely, an RF-exposure /
  Specific Absorption Rate (SAR) test report every jurisdiction below
  requires and isic-2620 does not, because a device with no intentional
  radiator has no SAR requirement. The general IEC 62368-1 safety
  baseline remains relevant to both.
- `cloud-itonami-isic-2610` -- semiconductor and electronics
  component/wafer fabrication, one tier UPSTREAM of both isic-2620 and
  this repo in the value chain.
- `cloud-itonami-isic-2910` -- manufacture of **motor vehicles**
  (structurally the closest sibling pattern -- vehicle assembly, type-
  approval/homologation, dual actuation, robotics-process-simulation --
  but a different final product and a different regulatory regime).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (display-stack
alignment vision check, OCA-lamination bonding press, bond-line defect
scan) operate under an actor that proposes actions and an independent
**Radio-Compliance Governor** that gates them. The governor never
issues a radio-conformity certificate itself; `:high`/`:safety-
critical` actions (`:actuation/ship-device-unit`, `:actuation/issue-
radio-conformity-certificate`) require human sign-off.

**Robot process simulation is a REAL, time-stepped physics simulation,
not a symbolic field comparison** (ADR-2607152000, extending
ADR-2607151600's automotive pilot and its fleet extension to this
vertical from day one, mirroring `cloud-itonami-isic-2930`'s/
`cloud-itonami-isic-2394`'s native-physics builds): `commsdevice.
robotics` walks every device-unit through a robot-executed display-
bonding verification mission (`kotoba.robotics` mission/action/
telemetry-proof contracts) -- display-stack alignment check, OCA-
lamination press-bond, bond-line defect scan -- before `:actuation/
ship-device-unit` is proposable, and grounds that mission's :passed?
verdict in an actual **display-module optical-bonding press
simulation**: a real, tested rigid-body physics engine
(`kotoba-lang/physics-2d`) time-steps a press-platen closing onto a
static display stack (cover glass + Optical Clear Adhesive + LCD/OLED
module) at a controlled velocity, and reads the peak collision impulse
as a real bonding-pressure reading (`:sim-peak-bonding-pressure-mpa`,
MPa) -- not an invented or hand-set number. The Radio-Compliance
Governor independently re-derives the device-unit's own `:sim-peak-
bonding-pressure-mpa` against a real, disclosed acceptance band
(`commsdevice.robotics/min-bonding-pressure-mpa`/`max-bonding-pressure-
mpa`), never trusting the mission's self-reported verdict alone.

## Core contract

```text
device-unit intake + radio-compliance-rules verify + end-of-line quality screen
  -> Device Advisor proposal
  -> Radio-Compliance Governor (HARD holds un-overridable)
  -> phase gate (actuation always escalates)
  -> human approval for high stakes
  -> append-only ledger + draft records
```

## Actuation honesty

Shipping a device-unit via a robot handling/packout action and issuing
a radio-conformity certificate produce **unsigned draft records and
ledger facts only**. This actor does not talk to real plant control
systems or type-approval-authority portals (MIC/TELEC, FCC, notified
bodies, OPSS/Ofcom). Signature and hardware dispatch are the
communication-equipment plant's own acts.

## Ops

| Op | Effect |
|---|---|
| `:device-unit/intake` | normalize device-unit directory patch (phase 3 may auto-commit when clean) |
| `:radio-compliance-rules/verify` | per-jurisdiction Giteki/RED/FCC radio-type-approval evidence checklist (always human) |
| `:end-of-line-quality/screen` | end-of-line defect screen (HARD hold if unresolved) |
| `:robotics/simulate-display-bonding` | REAL `physics-2d`-simulated display-module optical-bonding press mission (always human; required on file before shipment) |
| `:actuation/ship-device-unit` | draft device-unit-shipment record (always human; HARD hold if robotics-sim missing, independently out-of-tolerance, or RF power out of range) |
| `:actuation/issue-radio-conformity-certificate` | draft radio-conformity-certificate record (always human) |

## Social / regulatory hand-off

```clojure
(require '[commsdevice.store :as store]
         '[commsdevice.export :as export])

(def db (store/seed-db))
(export/audit-package db)           ;; EDN maps for radio-type-approval/regulator hand-off
(export/package->csv-bundle db)     ;; CSV bundle (device-units/ledger/shipments/radio-conformity-certificates)
```

Operator console (static sample): `docs/samples/operator-console.html`.

## Develop

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## License

AGPL-3.0-or-later — see `LICENSE`.

## Operator console (Pages)

After enabling GitHub Pages (Settings → Pages → GitHub Actions), the
static console is at:

https://cloud-itonami.github.io/cloud-itonami-isic-2630/

Local: open `docs/index.html` or `docs/samples/operator-console.html`.

## Export audit package (CLI)

```bash
clojure -M:dev:export
# or: clojure -M:dev:export /tmp/audit-2630
```

Writes CSV files under `out/audit-package/` (or the given directory).
