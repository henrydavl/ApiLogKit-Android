# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| 0.2.x   | Yes       |
| 0.1.x   | No        |

## Reporting a vulnerability

**Do not open a public issue for security vulnerabilities.**

Please report them privately by emailing the maintainer or using [GitHub's private vulnerability reporting](https://github.com/henrydavl/ApiLogKit-Android/security/advisories/new).

Include:
- A description of the vulnerability
- Steps to reproduce
- Potential impact

You can expect an acknowledgement within 72 hours and a resolution timeline once the report is triaged.

## Notes

ApiLogKit is a debug-only library intended for development builds. It should never be enabled in
production. Ensure `ApiLogger.isEnabled` is gated on a debug/dev check in your app (for example
`ApiLogger.isEnabled = BuildConfig.DEBUG`). Because it captures full request/response bodies and
headers in memory, leaving it enabled in a release build could expose sensitive data.
