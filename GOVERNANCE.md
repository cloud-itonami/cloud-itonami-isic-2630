# Governance

`cloud-itonami-isic-2630` is an OSS open-business blueprint for
communication-equipment (smartphone/handset) manufacturing enablement,
robotics-premised.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a robot action the governor refuses is never dispatched to hardware.
- the Radio-Compliance Governor remains independent of the Device Advisor.
- hard policy violations (force-ship, record-suppression, unauthorized
  disclosure, or issuing a radio-conformity certificate without complete
  evidence) cannot be overridden by human approval.
- every ship, certification, sign-off, record and disclose path is
  auditable.
- sensitive design, production and personal data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, robot-safety, audit
and data-flow review.

Certified operators can lose certification for:
- bypassing robot-safety or record policy checks
- mishandling sensitive data
- misrepresenting certification status
- failing to respond to security or safety incidents
