# ADR-0001: Device Advisor ⊣ Radio-Compliance Governor architecture

- Status: Accepted (2026-07-16)
- Repository: `cloud-itonami-isic-2630` (ISIC Rev.5 `2630`)

## Context

Communication-equipment manufacturing (smartphone/handset final
assembly, display-module optical-bonding, end-of-line quality
inspection, radio-type-approval evidence verification, radio-
conformity-certificate issuance) needs the same governed-actor pattern
as the rest of the cloud-itonami fleet: an untrusted advisor proposes;
an independent governor may HOLD; high-stakes actuation never
auto-commits.

The industry registry's `2630` entry had sat at `:maturity :spec` with
a dead `gftdcojp/cloud-itonami-C2630` placeholder URL. `cloud-itonami-
isic-2620` (computers/peripheral equipment) already modeled the
adjacent final-assembly tier, but its EMC/product-safety self-
declaration catalog does not cover a device with an actual RADIO
TRANSMITTER -- a materially different, stricter regulatory regime
(Giteki/RED/FCC type-approval + SAR). This repository closes that gap
as its own actor, not a variant of isic-2620.

This is also the first isic-2630-tier repo built to this fleet's
current full standard from day one: a REAL time-stepped `physics-2d`
rigid-body simulation of the display-module optical-bonding press
(ADR-2607151600/ADR-2607152000's pattern, established by
`cloud-itonami-isic-2910`'s pilot and extended natively-from-day-one by
`cloud-itonami-isic-2930`/`cloud-itonami-isic-2394`), not a symbolic
field comparison retrofitted later.

## Decision

1. Namespaces live under `commsdevice.*` with the standard
   facts / registry / store / governor / phase / advisor / operation /
   sim / robotics / export shape.
2. Entity is a **device-unit** (a smartphone/handset production unit
   or lot), not a vehicle, part-lot, aircraft assembly or cement
   batch.
3. Dual actuation on the same entity:
   - `:actuation/ship-device-unit` (robot handling/packout dispatch draft)
   - `:actuation/issue-radio-conformity-certificate` (Giteki/RED/FCC-
     style radio-type-approval conformity certificate draft, distinct
     from isic-2620's general Declaration of Conformity)
4. Double-actuation guards use dedicated booleans
   (`:device-unit-shipped?`, `:radio-conformity-certified?`), never a
   status lifecycle (ADR-2607071320 / 6492 lesson).
5. `device-rf-power-out-of-range?` continues the fleet two-sided range
   check family (after testlab / conservation / water / steelworks /
   turbine / automotive's vehicle-emissions / autoparts' part-lot-
   DPPM), applied here to a device-unit's own measured RF conducted-
   transmit-power deviation (dB) against its own recorded production-
   test tolerance band -- a metric genuinely specific to a RADIO
   transmitter, unlike isic-2620's general IT-equipment fields.
6. `commsdevice.robotics` delivers ADR-2607151600/ADR-2607152000's
   real-physics-simulation pattern from day one: a real `physics-2d`
   press-platen `Body2D` collides with a static display-stack
   `Body2D` (cover glass + OCA + LCD/OLED module), `world-step`
   actually integrates/collides/resolves the contact over real ticks,
   and `:sim-peak-bonding-pressure-mpa` is read directly off the
   actual simulated trajectory -- mirroring `cementmill.robotics`'s
   press-collision technique (`cloud-itonami-isic-2394`) exactly, one
   tier closer to consumer-electronics manufacturing. The acceptance
   band (`min-bonding-pressure-mpa`/`max-bonding-pressure-mpa`, 0.15-
   0.55 MPa) is disclosed honestly as a reasoned engineering estimate
   for OCA-lamination bonding pressure, not a confidently-sourced
   single citation -- mirroring the retail drop-test ADR's 400g
   ceiling disclosure style.
7. Process-capability/end-of-line defect unresolved is evaluated
   unconditionally so `:end-of-line-quality/screen` itself can
   HARD-hold (parksafety ADR-2607071922 Decision 5 discipline, same as
   every prior sibling governor's end-of-line-defect-unresolved
   check).
8. Radio-type-approval evidence catalog seeds JPN (MIC/TELEC Giteki
   mark) / USA (FCC Part 15C + Part 22/24/27 Certification) / GBR
   (Radio Equipment Regulations 2017 / UKCA) / DEU (EU RED 2014/53/EU)
   only; missing jurisdictions are uncovered, never fabricated. Every
   seeded jurisdiction additionally requires an RF-exposure/SAR test
   report -- a real, radio-transmitter-specific evidence item with no
   analog in isic-2620's catalog.

## Consequences

(+) The smartphone/communication-equipment tier gains a forkable OSS
operating stack with auditable governor holds, closing the dead-
placeholder gap the industry registry's `2630` entry had.
(+) Built to the current full fleet standard from day one: governed
actor + real physics-2d simulation + WebGPU/WebGL2 render proof, not a
retrofit.
(+) Clearly distinguishes its regulatory regime from the adjacent
`cloud-itonami-isic-2620` sibling (radio-type-approval + SAR vs.
general EMC/product-safety self-declaration).
(−) No physical plant digital-twin tick in this repo (follow-up domain
data is out of scope here).
(−) Radio-type-approval-authority coverage is a starting catalog (4
jurisdictions), not exhaustive, and does not capture every carrier-
specific (e.g. individual MNO) certification supplement.
(−) `physics-2d` has no material-stiffness/adhesive-compliance model,
so the OCA layer's own real viscoelastic behavior cannot itself vary
the simulated reading (honestly disclosed in `commsdevice.robotics`'s
own docstring, mirroring every sibling's own disclosure of this same
engine limitation).

## Related

- ADR-2607011000 (robotics premise + ISIC coverage)
- ADR-2607151600 (real-engineering-simulation integration pilot --
  `cloud-itonami-isic-2910`)
- ADR-2607152000 (real-engineering-simulation fleet extension --
  `cloud-itonami-isic-2930`/`cloud-itonami-isic-2394`, this repo's
  direct structural template)
- Superproject fleet ADR for this promotion:
  `90-docs/adr/2607160100-cloud-itonami-isic-2630-smartphone-comms.md`
- Sibling architecture: `cloud-itonami-isic-2910` docs/adr/0001,
  `cloud-itonami-isic-2930` docs/adr/0001, `cloud-itonami-isic-2620`
  docs/adr/0001 (the isic-2620 README's "Scope note" is the direct
  counterpart to this ADR's Context section)
