# Security Policy

This project handles communication-equipment (smartphone/handset)
manufacturing, radio-type-approval evidence and shipment workflows.
Treat vulnerabilities as potentially high impact even when the demo
data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real device-unit, radio-test or customer data exposure
- authorization bypass
- Radio-Compliance Governor bypass
- audit-ledger tampering
- over-disclosure in reports or exports
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on device-unit data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real device-unit, radio-test and personal data outside this
  repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
