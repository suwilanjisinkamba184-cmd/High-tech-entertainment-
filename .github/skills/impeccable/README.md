# Impeccable — project design skill

This repository includes the upstream Impeccable skill for GitHub Copilot in `.github/skills/impeccable/`, together with its command reference playbooks.

- Upstream project: https://github.com/pbakaus/impeccable
- Documentation: https://impeccable.style/
- Install the full CLI and provider hooks from the repository root with `npx impeccable install`.
- Add the shared skill to another compatible agent with `npx skills add pbakaus/impeccable --skill impeccable`.
- For design work in this repository, start with `PRODUCT.md` and `DESIGN.md`, then use `$impeccable` / `/impeccable` with commands such as `critique`, `audit`, `polish`, `harden`, and `adapt`.

The checked-in skill and references are project instructions. The standalone CLI detector and automatic hook runtime are installed separately by the upstream installer; do not claim those hooks are active until that installer has run and the coding agent has approved them.
