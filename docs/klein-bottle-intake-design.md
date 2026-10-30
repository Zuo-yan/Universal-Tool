# Klein Bottle Item Intake

Status: implemented for Universal Tool 1.3.0.
Date: 2026-10-30.

## Confirmed decisions

- Provide both player-requested container transfer and separately switchable pickup auto-deposit.
- Replace the terminal's clear-search action button with the pickup auto-deposit toggle.
- Pickup auto-deposit defaults to off.
- Container transfer supports chest-like storage containers, including modded equivalents. Processing inventories such as furnaces are outside the first release.
- Pickup auto-deposit only accepts item types already present in the player's warehouse. New item types follow ordinary inventory pickup.
- A Klein Bottle in the player's inventory or offhand enables the feature when its toggle is on.
- Invoke container transfer by holding a Klein Bottle and Shift-right-clicking a supported storage block. Only the targeted block container is used.
- Manual container transfer moves all acceptable items, including new types; rejected items remain in the source container.
- Remember pickup auto-deposit state per player across reconnects and bottle replacements. First-time state is off.
- Only types with current warehouse stock match pickup auto-deposit. Taking the last item out removes that type from eligibility; no historical type list is maintained.
- Items deliberately dropped by the player are eligible for auto-deposit on subsequent normal pickup, subject to the same matching rules.

## Existing behavior relevant to the design

- Warehouses are associated with players, rather than individual bottle items.
- Warehouse item identity includes item components.
- Klein Bottles cannot be inserted into the warehouse.
- The search field already supports clearing through right-click independently of the action button.
- Existing stored container items retain their contents as opaque components; automatically unpacking nested containers has not been agreed.

## Proposed compatibility and interaction contract for final confirmation

- Support ordinary chest-like block storage through NeoForge item handlers, including modded equivalents that expose compatible storage slots. Special mod interfaces may require explicit adapters; support for every future mod is not guaranteed.
- Container transfer requires a Klein Bottle in the interacting hand and Shift-right-click. It targets only the clicked block's accessible item-handler slots and excludes processing blocks such as furnaces, brewing stands, and crafters.
- Normal chests and opened loot chests follow the same transfer rules. Do not access unopened loot or bypass normal container access and slot restrictions.
- Nested container items move intact with their existing contents. Do not recursively unpack their contents.
- When auto-deposit rejects an item, preserve the unaccepted quantity for ordinary pickup. When manual transfer rejects an item, leave it in the source container.
- Auto-deposit does not increase pickup range or bypass pickup delays, ownership restrictions, or other normal pickup eligibility checks.
- The toggle has distinct enabled and disabled appearances and a tooltip describing matching-stock behavior. Right-click clearing of the search field remains available.

## Implementation facts to account for later

- The warehouse currently removes item-type membership when the last item of that type is extracted. Historical matching would require additional persisted state if chosen.
- The current save path omits empty warehouses. Persisting player preferences must also preserve settings for a player whose warehouse is empty.
- The existing v1 design treats nested container items as opaque items; this feature does not yet authorize unpacking their contents.

## Acceptance checks

- Preserve item quantities and components across each transfer.
- Preserve rejected items in their source.
- Cover manual transfer of new types, inaccessible slots, duplicate requests, container closure, and removal of the required bottle.
- Verify chest-like menus without targeting player slots or machine processing slots; report the actual mod interfaces tested.
- Verify pickup with the toggle off/on, a bottle in inventory/offhand, no bottle, existing stock, exhausted stock, component mismatches, and deliberately dropped items.
- Verify preference persistence across reconnects, bottle replacement, and an empty warehouse; existing saves default to off.
- Verify rejected and partially accepted pickup quantities and ordinary pickup restrictions.
- Verify nested container contents remain intact and Klein Bottles remain forbidden from storage.
- Verify Shift-right-click on chests, double chests, modded item-handler storage, locked containers, unopened loot containers, and excluded processing blocks; bump project version and relevant protocol fields when implementing behavior changes.
