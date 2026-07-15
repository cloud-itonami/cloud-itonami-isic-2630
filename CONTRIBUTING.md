# Contributing

`cloud-itonami-isic-2630` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development
The capability layer lives in `kotoba-lang/*` libraries (including the
real `kotoba-lang/physics-2d` time-stepped rigid-body engine this actor's
`commsdevice.robotics` calls). This repo holds the business blueprint
and operator contracts.

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## Rules
- Do not commit real device-unit, radio-test, personal or credential
  data.
- Keep robot dispatch, records and disclosures behind the
  Radio-Compliance Governor.
- Treat workflows as high-risk: add tests for robot-safety gating,
  record integrity, disclosure and audit logging.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs
need updates.
