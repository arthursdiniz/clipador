# Security Policy

## Reporting a vulnerability

Do not open a public issue containing credentials, personal data, private video URLs, exploit details, or uploaded media. Contact the repository owner privately and include only the minimum information needed to reproduce the problem.

## Repository safety

- Never commit `.env.local`, `.env`, database files, downloaded videos, generated clips, transcripts, local models, or executable tools.
- Keep only sanitized example configuration in `.env.example` and `.env.local.example`.
- Run `./infra/local/Test-GitSafety.ps1` before every push.
- Enable GitHub secret scanning and push protection when the repository settings make them available.
- If a secret is committed, revoke or rotate it immediately. Removing it from the latest commit does not remove it from Git history.

## Supported versions

The current `main` branch receives security fixes during initial development.
