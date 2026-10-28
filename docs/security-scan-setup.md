# Secure NVD scan setup

The existing Maven security scan cannot initialize its NVD database without a valid NVD credential. The prepared workflow reads an Actions repository secret named `NVD_API_KEY` only in the credential check and scanner step. The Maven command contains the environment variable name, never the key value. OWASP's `nvdApiKeyEnvironmentVariable` parameter is used to avoid command-line disclosure. Do not enable Maven debug logging or publish environment files.

The owner must request/activate a free key at https://nvd.nist.gov/developers/request-an-api-key and save it through https://github.com/m-idriss/3dime-api/settings/secrets/actions/new with the name `NVD_API_KEY`. Never paste the key into an issue, chat, source file, command argument, screenshot or workflow log. No credential is included in this change.

This PR remains a draft and must not be merged to trigger a backend production release. Once the secret is configured, use the existing Security Scan workflow's manual Run workflow control with this PR's branch selected, then verify Maven dependency scanning and container scanning. The scan can consume up to the existing 35-minute timeout per job. Missing credentials fail with a fixed, non-sensitive explanation. Existing vulnerability thresholds, scanners and reporting behavior are unchanged.

Local verification is static YAML/command inspection only. Maven/NVD scanning, real credential validity and backend production behavior are not verified by this preparation.
