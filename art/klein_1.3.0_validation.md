# Klein Bottle Item Intake 1.3.0 validation - 2026-10-30

- Platform: Minecraft 1.21.1 / NeoForge 21.1.251 / Java 21.
- Artifact: `build/libs/universal_tool-1.3.0.jar`.
- SHA-256: `17B1686E7A7B0438F8705EEDED360C5492E6CD14F05FEBF78C0B899611F4855C`.
- Build and GameTests: `gradlew.bat build runIslandGameTest` passed. All 49 required tests passed.
- Log: `build/klein-intake-final.log`.
- Changed-file whitespace checks passed.

## Covered behavior

- Per-player auto-deposit preference defaults off and persists when the warehouse is empty.
- Pickup routing accepts only an existing component-exact type and consumes the matching entity quantity.
- Pickup routing rejects component mismatches, exhausted types, and Klein Bottle sources.
- Container transfer moves storable items, leaves Klein Bottles behind, and preserves a nested container with its components.
- Existing storage, synchronization, extraction, crafting, anvil, island, skill, and repair regressions remain green.

## Verification boundary

- Final client rendering and input changes compile. No interactive client run or restart was performed in this turn.
- Modded container support is based on standard visible container slots with normal access checks. Special interfaces that expose no compatible slots require a dedicated adapter.
- Protocol is 14; multiplayer clients and servers must use matching versions.
- The live save was not modified by automated tests.
