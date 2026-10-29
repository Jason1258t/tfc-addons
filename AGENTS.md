# tfc-addons — pack addons (NeoForge mods)

Addons for the TerraFirmaCraft RealWorld pack of «TFC Планы» (Minecraft 1.21.1, NeoForge). One folder per addon
(`<name>-mod/`: Gradle project, `addon.json` site card, `icons/`, `release.sh`).

**Where this repo lives.** It is checked out as the `tools/` git submodule of the site repo
[tfc-plans](https://github.com/Jason1258t/tfc-plans) and is normally worked on from there: release scripts call
`../../scripts/mcp/render.mjs`, MCP `tfc-pack` reads `tools/`, the site build reads `tools/*/addon.json`. Agent rules,
workflow and skills (`addon-dev`, `addon-release`) — in that repo: `AGENTS.md`, `docs/addons.md`,
`docs/state/addons.md`, `.claude/skills/`.

- **Workflow (from a tfc-plans worktree):** `tools/` is a submodule on a detached commit. Before editing:
  `git -C tools switch -c feat/<short> origin/main`; commit there (message `Аддон <mod>: что сделано`), push it and
  merge into `main` of this repo (`git -C tools push origin HEAD:main` when it is a fast-forward), then commit the new
  `tools` pointer in the tfc-plans branch. The tfc-plans `ship` tool refuses a `tools` pointer that is not on
  `origin/main` here.
- Versions come from the pack (jars in the snapshot cache, `npm run pack:fetch` in tfc-plans), not "latest". Game facts
  about target mods — from their jars via MCP (`class_info`, `decompile`, `code_refs`, `mixins`), not from memory.
- Edited resources → MCP `resources_check addon=<path to the mod folder>` and look at `model_render` (pass
  `addon=<path>` when working in a git worktree). Translation keys from code need `./gradlew compileJava` first.
- Every key must exist in `ru_ru` (players see Russian).
- Site card — `addon.json`, site page — guide `addon-<mod>` (MCP `guide_get`/`guide_put`); release steps — skill
  `addon-release`, final check — MCP `addon_check`.
- Icons for the site are drawn by `node scripts/mcp/render.mjs <addon folder> <ids…>` of tfc-plans (release scripts
  call it).
- Jars are not in git (`*/*.jar`): every version is on the pack server, the current one is found by `sha256` from
  `addon.json`.
- Nothing can be checked in game: report what the dev server verified and mark the rest «в игре не проверялось».
