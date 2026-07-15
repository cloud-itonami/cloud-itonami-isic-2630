# Operator Guide

## First Deployment
1. Register radio-compliance engineers, plants, device-units,
   personnel and robots.
2. Import historical device-unit / end-of-line / radio-type-approval
   records.
3. Run read-only validation and robot mission dry-runs (display-
   bonding-press simulation).
4. Configure radio-type-approval evidence checklists and human
   sign-off paths.
5. Publish a dry-run audit export.

## Minimum Production Controls
- governor gate on every robot action before dispatch
- human sign-off for `:high`/`:safety-critical` robot actions (e.g.
  device-unit shipment, radio-conformity-certificate issuance)
- audit export for every ship, sign-off and disclosure
- backup manual process

## Certification
Certified operators must prove robot-safety integrity, evidence-backed
records and human review for compliance-affecting actions.

## Operating states
intake : radio-compliance-rules-verify : end-of-line-quality-screen : robotics-simulate-display-bonding : approve : ship-device-unit : issue-radio-conformity-certificate : audit

## Audit export (social operation)

After a production session, export the append-only package for
radio-type-approval inspectors or internal compliance:

```clojure
(require '[commsdevice.store :as store]
         '[commsdevice.export :as export])
(export/audit-package store)        ; EDN maps
(export/package->csv-bundle store)  ; CSV files as string map
```

Drafts remain **unsigned** — signing and submission to a radio-type-
approval authority are the communication-equipment manufacturer's own
acts (see README Actuation honesty).

Static UI sample: `docs/samples/operator-console.html`.
