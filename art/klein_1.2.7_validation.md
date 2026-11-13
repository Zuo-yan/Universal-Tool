# Klein Bottle 1.2.7 validation — 2026-10-27

- Platform: Minecraft 1.21.1 / NeoForge 21.1.251 / Java 21.
- Artifact: `build/libs/universal_tool-1.2.7.jar`.
- Build: `gradlew.bat build` passed. Final log: `build/klein-1.2.7-build.log`.
- GameTests: `gradlew.bat build runIslandGameTest` passed all 45 required tests. Log: `build/klein-1.2.7-validation.log`.
- Changed-file whitespace checks passed.

## Regression coverage

- All inventory Klein Bottles use the same forbidden-storage predicate as warehouse insertion. Ordinary items and empty slots are not marked. Other bottles remain movable in the player inventory; the opening bottle remains protected.
- Shift-click and carried-stack insertion reject other bottles without consuming them.
- A 513-entry, 57-row snapshot is resent in 64-entry packets three times, including actual payload encoding/decoding. The displayed list remains complete and row 52 remains selected after every packet.
- A 65-entry initial snapshot is published only after its final page.
- A completed snapshot shrinking to exactly 64 entries clamps the scroll to row 3; an empty snapshot resets it to row 0.

## Client behavior and verification boundary

- All forbidden inventory items render the existing locked frame and a lock badge above the item icon.
- Scrollbar drawing and dragging share thumb dimensions; dragging retains the initial grab offset, including positions quantized to the same screen pixel in large lists.
- Wheel input scrolls rows and accumulates fractional deltas. Shift+wheel retains the existing item-transfer shortcut.
- Final client rendering/input edits compile successfully. No interactive client verification or restart of the user's running game was performed; that process still has the earlier classes loaded.
- Mod network protocol is now 13; multiplayer clients and server must use matching versions.
- Existing unrelated working-tree changes were preserved. No commit or release publication was performed.
