# Klein Bottle Terminal - Design Specification v1

Status: proposed for final design approval. No gameplay implementation is authorized yet.

## 1. Scope

- Add `universal_tool:klein_bottle`, a one-per-stack handheld terminal for Minecraft 1.21.1 NeoForge.
- Open with use/right-click from either hand. Lock the exact hand slot while its menu is open.
- Every bottle owned or obtained by a player opens that player's own warehouse. UUID, not item NBT or bottle ownership text, determines the warehouse.
- Losing, dropping, trading, or destroying a bottle never deletes the warehouse. A new bottle accesses the same player's warehouse.
- First release stores items only. It has no fluid storage, autocrafting, JEI recipe transfer, automation capability, or Refined Storage dependency.
- Add a survival recipe and a normal entry in the Universal Tool creative tab. Rarity: Epic. Network protocol version and mod version must be updated when implementation begins.

## 2. Confirmed Visual Direction

- Reference: `art/references/klein-bottle-terminal-ui-v1.png`, supplied and approved by the user.
- Theme: mathematical instrument crossed with mysterious arcane relic.
- Ink-black smoked glass, deep petrol teal, cyan continuous-loop etching, sparse antique brass ticks and warm ivory text.
- Header: Klein bottle emblem, title, private-space label, close button.
- Toolbar: search, name/quantity sort, ascending/descending toggle.
- Main area: virtual aggregated item grid, compact quantities, complete count in tooltip, vertical scrollbar.
- Bottom: standard 9-column player inventory and hotbar; the opening bottle is visibly locked.
- Use the reference as art direction, not as a full-screen texture. Draw or atlas reusable frames, controls and ornaments. Preserve actual Minecraft inventory hitboxes and GUI-scale behavior.

## 3. Interaction Contract

- Search filters display names and item IDs. It is case-insensitive and runs on the complete synchronized type index, not just the visible page.
- Name and quantity sorting support both directions. Name sorting uses the locale-aware display name with item ID as a stable tie-breaker.
- Click a virtual item with an empty cursor: extract up to one item-type stack limit. Right-click: extract up to half that limit, at least one when available.
- Click a virtual item while carrying the same type: insert the carried stack. Right-click: insert one.
- Click an empty virtual cell while carrying an item: insert the carried stack.
- Shift-click a player slot: insert the full stack into the warehouse. The source slot changes only by the amount actually accepted.
- Shift-click a virtual item: repeatedly extract real stacks into valid player-inventory space. Stop when the type is exhausted or no space remains.
- Mouse wheel over a virtual item: transfer one item between warehouse and inventory in the displayed direction. Holding Shift transfers a full real stack where possible.
- Durability-damaged or otherwise unstackable items extract as individual instances with their own components intact.
- No physical warehouse `Slot` ever contains more than the item's legal `ItemStack` count. The client never turns a huge aggregate count into a huge `ItemStack`.
- Search, sort, and scrolling never create, delete, or reorder stored item instances.

## 4. Storage Model

- `KleinStorageSavedData`, stored in the overworld `DimensionDataStorage`, owns `Map<UUID, KleinWarehouse>`.
- Each warehouse contains stable `long` entry IDs, an item-type index, and a linked ordered map of entries.
- A stackable item type is defined by item plus its complete component patch. Different names, enchantments, custom data, or other components are different types.
- Every stackable entry holds one canonical stack template with count 1 and a `BigInteger amount`.
- A damaged tool or an item whose maximum stack size is 1 is stored as an individual entry with count 1 and its complete canonical stack. This avoids merging different damage or state.
- Serialization uses `ItemStack.CODEC` for the template and a decimal string for `BigInteger`. Do not use `long`, floating point, scientific notation, or item-NBT-only storage.
- Reject malformed, zero, negative, or unbounded input lengths while loading. Preserve unknown mod item IDs as invalid/unavailable rather than silently replacing them with another item.
- Save a format version from the first release. Do not overwrite data that declares a newer unsupported version.
- The Klein bottle itself is rejected by every insertion route, including shift-click, cursor insert, scrolling, and any future internal transfer helper. This is a hard domain invariant, not merely a disabled UI button.
- Other containers retain their complete contents as opaque item components. Do not expand, recursively count, or alter them.
- There is no configured gameplay cap on item types or per-type quantity. Physical memory, save size, processing time, and protocol limits remain real engineering limits; the UI must not imply infinite resources are free.

## 5. Server Authority and Concurrency

- The server alone mutates inventory, cursor, warehouse amounts, and extraction results.
- Clients send semantic requests: entry ID plus operation, or source slot plus operation. They never send an item stack, warehouse owner UUID, arbitrary count, or expected new total as authority.
- Resolve an entry ID on the server and verify that its current canonical type matches the synchronized generation known to that menu. Ignore stale or invalid requests.
- Revalidate the opening hand slot on every action. It must still contain the same Klein bottle instance opened by that menu. Lock it against click, number-key swap, quick-move, drag, and pickup-all actions.
- Use a per-menu monotonically increasing request sequence. Accept each sequence once, in order; ignore duplicates, gaps that are not yet expected, and stale responses.
- Execute inventory and warehouse mutation on the server thread in one synchronous operation. Do not simulate on the client, optimistically subtract stock, or split deduction and insertion across ticks.
- Compute the actual extractable amount from warehouse stock, real stack limits, legal player-inventory destinations, and the locked bottle exclusion. Only then remove that exact amount from the warehouse.
- Insertion first verifies and removes the actual source items, then adds the same amount. The Klein-bottle rejection is checked before either side changes.
- Two open menus for one player observe the same warehouse. Different players can never address each other's entries, even by guessing IDs.
- On invalid requests or menu close, resynchronize the real cursor and player inventory. Never treat a client prediction as a rollback mechanism.

## 6. Synchronization and Large Quantities

- Open with a small type index containing entry ID, display-sort metadata, and an arbitrarily large decimal quantity where needed. Do not send complete component payloads for every type up front.
- Request canonical stack templates only for visible entries and hovered tooltips. Cache them client-side and invalidate them on entry removal or type changes.
- Send bounded initial pages and incremental entry changes. No packet or single operation may iterate an unlimited number of entries or extract an unlimited number of stacks.
- Use fixed packet-count and decoded-byte budgets, checked before allocation. Limit search string length, page size, operations per tick, and stack moves per request.
- Quantity strings use canonical unsigned decimal with an explicit maximum encoded length sufficient for practical play. Reaching that technical encoding ceiling is a defined error, not overflow or wraparound; it is not a configured gameplay capacity.
- Search and sort are client-side presentation over synchronized index data. The server remains authoritative for item extraction and stock.
- Multiple viewers receive only changed entries, not a complete warehouse snapshot after every click.

## 7. Recipe and Resources

Shaped recipe:

```text
P G P
E N E
P G P
```

- `P`: `minecraft:purpur_block`
- `G`: `minecraft:glass`
- `E`: `minecraft:ender_pearl`
- `N`: `minecraft:nether_star`
- Result: one `universal_tool:klein_bottle`.
- Supply Chinese and English translations, item model, pixel-art bottle icon, recipe, reusable GUI assets, and a small lock indicator.

## 8. Implementation Boundaries

- Study Refined Storage 2's `v2.0.0-milestone.4.1` architecture: resource aggregation, virtual grid, server-side operations, and opening-slot locking.
- Write an independent implementation in `org.gwfx.universaltool.klein`, with registration, menu, network, client, and assets integrated through existing project patterns.
- Do not copy Refined Storage classes wholesale, expose its network model, or depend on its internal APIs. Preserve its MIT notice if any adapted code or resource requires it.
- New common classes must not load client-only screen classes on a dedicated server.
- Design approval precedes gameplay edits. Implementation then requires focused tests, a full build, runtime verification, and actual in-game UI screenshots before being called complete.

## 9. Acceptance Tests

- Empty and populated warehouses survive world save/reload, including a count greater than `Long.MAX_VALUE`.
- Stackable components remain distinct; damaged tools never merge; shulker contents round-trip byte-for-byte through canonical stack data.
- Warehouse types are isolated by UUID and shared across multiple bottles for one player.
- Every insertion path rejects a Klein bottle without changing source or warehouse state.
- Cursor, shift-click, right-click, mouse wheel, and inventory-full behavior conserve total items exactly.
- The opening bottle cannot be moved or replaced while open; loss of that exact item invalidates further actions safely.
- Duplicate, stale, forged, and cross-player packets cannot create items or mutate another warehouse.
- Two viewers see consistent changes. Closing a menu leaves no carried ghost stack.
- Search, both sorts, both directions, scrollbar paging, compact counts, complete-count tooltips, and GUI scaling remain usable.
- No client-only class loading on a dedicated server; existing tests and full Gradle build remain green.

## 10. Final Design Confirmation

The complete scope above requires explicit user confirmation before implementation. Later corrections are recorded as a new design revision rather than silently changing approved behavior.
