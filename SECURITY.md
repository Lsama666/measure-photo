# Security and privacy

The app stores site photos and annotations locally. The main manifest does not request INTERNET; Android backup is disabled. Exports are visible in destinations chosen by the user and may be shared by the operating system or other apps.

Do not publish actual project data in an issue. Use GitHub private vulnerability reporting via the repository Security tab for a security issue. For ordinary bugs, open a public issue with synthetic data.

Review currently focuses on the default branch. No security audit or guaranteed response SLA is claimed. Areas needing review include malformed image imports, file/URI handling, Room persistence, export cancellation and resource exhaustion.
