# Changelog

## 1.10.11 - 2026-11-24

- **白厄终结技「永劫燔世，其将背负」三幕电影级 3D 视觉与运镜全面重构**：
  - 使用 Blockbench 5.2.2 自由网格新建 6 套终结技专属 3D 模型（白蓝圣翼、黄金左神翼、黑曜石右残翼、28×16 旋转太阳茧球、双赤道超长撕裂彗尾环、26 格暗化苍穹球穹）并精绘配套 7 张高清 VFX 贴图。
  - **第一幕（白蓝圣翼升空）**：正面略仰电影运镜，展开对称白蓝天使光翼、头顶锯齿烈日轮与脚下圣光柱。
  - **第二幕（焚世金球旋舞）**：开启 26 格暗紫金红焚世苍穹压暗环境，环绕斜侧拉远仰拍，呈现双层反旋炽金焚世茧球、横贯画面的超长双彗尾环与 32 块黑褐碎石风暴。
  - **第三幕（破茧神魔双翼降临）**：切回正面神明全景，叠加破茧瞬闪、FOV 爆推与阻尼微震；背后展开「左炽烈黄金圣翼 + 右黑曜石暗紫金焰残翼」（右翼采用实体暗紫剪影 + Additive 炽金羽缘双 Pass 混绘），头后悬浮双层反旋烈日圣环，周身环绕 12 枚 4×3 图集黄金泰坦神徽。
  - 觉醒形态持续时长延长至 **20 秒**（`OPENING_TICKS + 400`），演出期间按 `F5` 可随时退出自由视角，结束自动平滑还原原视角。

## 1.10.10 - 2026-11-24

- 「永劫燔世，其将背负」加入起手与觉醒语音，以及服务端控制的实际悬停。变身期间锁定位移、保留转向，结束后落回并恢复控制。
- 调整举剑、悬停和觉醒姿态，并加强金红背光与紫蓝光铠的轮廓。
- 打开主动技能轮盘时将鼠标归中，保留当前技能高亮，避免上次的鼠标位置触发跳选。

## 1.10.9 - 2026-11-24

- 修复长按选择键时主动技能轮盘反复开关的问题。松开选择键确认，取消后需重新按下才会打开。
- 修复鼠标停留时覆盖滚轮选择、同步装配时高亮错位，以及选择技能打断蓄力后继续持键误触施放的问题。
- 重绘星轨轮盘，缩小并分开技能卡，在中心展示高亮技能的等级、觉醒和冷却状态，并补全中英文操作提示。

## 1.10.8 - 2026-11-24

- 「永劫燔世，其将背负」新增 Blockbench 扭转能量带、分瓣光壳与立体放射光流，使用两张流动透明贴图同步表现聚能、解体与爆发。

## 1.10.7 - 2026-11-24

- **全量技能三级数值一致性审计与「超维直达」增强**：
  - 彻底区分 **超维直达 (`hyper_conduit`)** 三级效果并更新文案：Lv.1 直入背包 + 已有库存入瓶；Lv.2 携带克莱因瓶时全部可存掉落与地面拾取自动直达超维仓储（`KleinPickupHandler` 同步直通）；Lv.3 觉醒额外直接吸收 +25% 挖矿/击杀经验；补充物品入瓶微音反馈与背包刷新。
  - 修复 **兵刃共鸣 (`tool_resonance`)** Lv.3 耐久自愈频率错误（`80/3=26` 对 `%20` 永不触发），调整为 3s/2s/1s 稳定自愈。
  - 修复 **生存回流 (`survival_flow`)** 实际每 3 秒才回一次血的偏差，恢复为每秒精确回血 `0.5 / 1.0 / 1.5`。
  - 修复 **反击护体 (`counter_guard`)** 实际反伤为 `100%/125%/150%` 的偏差，对齐为 `125% / 150% / 175%`。
  - 补全 **专注采掘 (`focus_mining`)** Lv.3 觉醒的 `25%` 概率不消耗工具耐久与 **迅捷步伐 (`swift_pace`)** 疾跑/跳跃饥饿减耗实装，并对齐 **时空锚感 (`spatial_attunement`)** 数值说明。
  - 在 `SkillProgressionGameTests` 中新增全 32 技能三级差异校验与 `hyperConduitAndAuditedSkillEffects` 回归测试。

## 1.10.6 - 2026-11-24

- 「永劫燔世，其将背负」加入觉醒运镜、角色抬升、分层背光和实体岩片。演出结束自动回到原视角，F5 可提前切换。

## 1.10.5 - 2026-11-24

- 重做「永劫燔世，其将背负」的变身演出。觉醒后，侵晨切换至黑金开刃的独立剑型，背部展开深色光铠，光环与爆发碎片沿世界空间扩散。

## 1.10.4 - 2026-11-24

- 侵晨终结技在中英文界面采用原作名称「永劫燔世，其将背负」与 “He Who Bears the World Must Burn”。

## 1.10.3 - 2026-11-24

- 侵晨在攒满十二火种后可右键释放终结技，同步播放聚焰、举剑、双重冲光和棱晶环绕动画。

## 1.10.2 - 2026-11-24

- **星典图鉴穿模修复与「超维直达」掉落直达补全**：
  - 修复打开「星典图鉴（`SkillCodexModal`）」时底层星图界面继续绘制导致的文字穿模重叠，并分离图鉴卡片左右栏标题与试炼状态排版、内缩关闭按钮避免贴边。
  - 为被动技能 **「超维直达 (`hyper_conduit`)」** 接入 `BlockDropsEvent`、`LivingDropsEvent` 与 `LivingExperienceDropEvent`：普通挖矿、连锁采矿与击杀生物的掉落物跳过落地直接入包（携带克莱因瓶时按等级直达超维仓储），Lv.3 觉醒额外 +25% 挖掘/击杀原版经验并直接吸收不掉经验球；同步优化中英文技能说明文案。

## 1.10.1 - 2026-11-24

- **技能界面详情可见性与交互升级**：
  - 将右侧详情面板（`SkillDetailDrawer`）重构为完整的独立可滚动视口（支持鼠标滚轮与鎏金滚动条拖拽），完整展示试炼条件、核心参数、完整技能描述、`Lv.1 / Lv.2 / Lv.3` 全部阶梯数值、`★ 星辉觉醒` 质变词条与星座共鸣速览，彻底消除小尺寸窗口下的截断隐藏问题。
  - 新增 **「星典图鉴」全览弹窗（`SkillCodexModal`）**：可通过顶栏「★图鉴」按钮按星座筛选浏览全部 32 项技能的完整描述与三级数值，点击任意条目直达星图节点。
  - 为星图画布节点添加悬停富文本 Tooltip，悬停即可快速预览当前/Lv.1 数值、简介与觉醒摘要。
  - 修复 `skill_stat.universal_tool.astral_cleave` 中英文翻译文件中的转义百分号格式。

## 1.10.0 - 2026-11-24

- **技能模块全面重构（星辉秘仪 · 深空鎏金星盘）**：
  - **全存档开关与休眠机制**：新增 `/ut skill on | off | status` 管理员指令（默认开启，随存档持久化）；关闭后立即清除主被动运行时属性修饰符、隐藏 HUD、禁用轮盘与施放、暂停经验与试炼增长，完整保留存档进度。
  - **五阶星阶与渐进双轨解锁**：重构 50 级前快后稳成长曲线与五大星阶境界（星尘初醒 → 星轨行者 → 辉光执掌 → 界域织星 → 星穹主宰，槽位由 2/2 渐进扩展至 6/8），110 点预算精确覆盖全部 32 颗星；**被动技能不再默认全部解锁**，所有中/高/终极技能需满足「前置星图节点 + 可视化试炼进度」双轨条件。
  - **四大星座 × 32 技能与 Lv.3 星辉觉醒**：保留重做全部 20 个旧技能并新增 12 个技能（裂空星斩、天罚雷域、兵刃共鸣、圣域穹顶、凤凰余烬、虚空折跃、时空锚感、星脉连锁、炼金脉冲、超维直达、虚空眷顾、秘境直觉），深度联动万能工具、生化培养仓、时空法典、口袋界域、克莱因超维仓储、万能聚合台与万能抽奖机；所有技能 Lv.3 满级触发「星辉觉醒」质变词条。
  - **3 套星盘构筑预设与星座 2/4 件共鸣**：支持脱战 5 秒一键切换 3 套构筑预设，装配同星座达 2 / 4 星激活对应星座共鸣与星图光环。
  - **深空鎏金星盘 UI 与高冲击动效**：重写 `SkillScreen`（自由拖拽缩放星图、旋转观星仪、能量流光星轨、点亮爆发星环、试炼进度条、底部预设与星轨条）、`SkillWheelScreen`（弹性展开径向星盘）、动态星徽 HUD（扫光冷却、蓄力环、迷你副槽、经验微条）与定制 `SkillToastOverlay` 升级/解锁/觉醒横幅及粒子和弦反馈；生成全部 32 个 `32×32` 高精像素图标。
  - **历史缺陷根治**：全面修复重复冻结丢失原 AI、冻结玩家施放技能、友好生物/宠物保护、伤害归属、PVP 战意律动失效、放置方块过期与洗点误删外部急迫药水等问题。

## 1.9.40 - 2026-11-24

- 对白厄武器「侵晨 / Dawnmaker」（`universal_tool:qinchen_sword`）普通战技「黎明创世，地辟天开」视觉特效进行三合一深度重构：
  - 使用 Blockbench MCP 制作并导出 4 套 3D VFX 模型（`phainon_sunburst_wheel`、`phainon_crescent_wave`、`phainon_slash_beam`、`phainon_debris_shard`，`.bbmodel` 存于 `art/assets/`，`.obj` 存于 `assets/universal_tool/models/vfx/`）。
  - 对照原作关键帧升级高精度二次元发光贴图（512×512 太阳圣印、1024×512 蓝金弯月剑气 UV 带、512×256 贴地冲击气浪与金星划痕、64×64 暗色玄武岩碎石）。
  - 新增 `PhainonVfxRenderTypes`（`LIGHTNING_TRANSPARENCY` 叠加发光 + 关闭深度写入/背面剔除）与 `PhainonVfxModelData`（OBJ 预烘焙与 UV 流动支持），实现立体圣日轮与放射金芒、剑身金焰光锥、裂空青白光柱、3D 拱形蓝金弯月能量波、贴地气浪划痕、14 块立体飞溅碎石及蓄力/挥击 FOV 拉扯与微震镜头效果。
  - 将演出节奏调整为更贴合原作的华丽时长（蓄力 1.4s / 28 ticks，总演出 2.5s / 50 ticks，冷却 5.5s）。

## 1.9.39 - 2026-11-24

- 为白厄武器「侵晨 / Dawnmaker」（`universal_tool:qinchen_sword`）正式定名并新增普通状态右键战技「黎明创世，地辟天开 / Let There Be Light」：
  - 将物品本地化名称更正为「侵晨」（英文 `Dawnmaker`）。
  - 新增不可变武器状态组件 `PhainonWeaponState`（`universal_tool:phainon_weapon_state`），记录【火种 / Coreflame】（上限 12）与形态标记，为后续变身扩展预留边界。
  - 普通状态下右键触发起手日轮圣印、举剑蓄能、日轮绽放、疾步前冲与蓝金能量波扩散横扫，对主目标造成 14.0 物理伤害、对相邻及前方扇区目标造成 10.5 扩散伤害，每次施放获得 2 点【火种】（5 秒冷却），并随机播放两段白厄普通战技原声中文语音。
  - 新增客户端 3D 渲染器 `PhainonSkillEffectRenderer` 与手持动作扩展 `PhainonSwordClientExtensions`，实现地面日轮圣印阵、垂直日轮光环与四芒十字耀光、青白裂空射线、倾斜蓝金弯月能量波、柔和边缘金辉晕及第一/三人称举剑挥斩动画。

## 1.9.38 - 2026-11-24

- Localized the sword as 星锋剑 / Starblade, removed the extra rarity tooltip line, and adjusted the first-person grip.

## 1.9.37 - 2026-11-24

- Added the Qinchen Sword with its textured 3D model, Mythic rarity, infinite durability, 7 attack damage, and 7 attack speed.

## 1.9.36 - 2026-11-22

- **Gulf GT Sports Car Amphibious Visuals & Anti-Clipping Overhaul**:
  - Split `wheel_rl_brake` and `wheel_rr_brake` in `convert_sports_car_assets.py` / `gulf_gt_car.json` so rear brake discs and calipers fold horizontally together with the rear wheels, eliminating vertical brake clipping inside the wheel wells.
  - Upgraded `SportsCarModelData` and `SportsCarRenderer` with shared zero-clipping wheel-pod transforms (`computeWheelPodTransform`, `computeWaterMorphLift`), full-time carbon-fiber light-blocking wheel-well bulkheads with deployable armored louver panels, articulated double-wishbone & dual-stage hydraulic suspension arms, four independent ducted hover shrouds with Gulf orange/blue trim, compact center wave-piercing hydro skirts, and dynamic 6°–12° thrust-vectoring pod tilt synchronized across pods, shrouds, and cyan thruster glow rings.
  - Synchronized passenger/cockpit lift and expanded hover-pod splash ring coordinates in `SportsCarClientHandler`.

## 1.9.35 - 2026-11-19

- **Gulf GT Sports Car Amphibious Hover-Hydrofoil Mode**:
  - Added automatic water-land mode detection with hysteresis anti-jitter (`DATA_WATER_MODE`, `DATA_WATER_MORPH_PROGRESS`, `DATA_WATER_SURFACE_Y`) and smooth closed-loop spring-damper water-surface height tracking, eliminating vertical bobbing while gliding across water (`0.88` blocks/tick base, `1.38` blocks/tick with Nitro) and enabling water drifting (`Space`) and smooth shore climbing (`tryClimbShoreFromWater`).
  - Added two-stage wheel-to-hover-turbine folding animation (`SportsCarModelData`), deployable Gulf-livery hydrofoil skis with pulsing cyan hover thruster glow (`SportsCarRenderer`), multi-layer bow spray, V-wake, drift water curtains, and nitro water-jet particles with `[HYDRO]` ActionBar status (`SportsCarClientHandler`).
  - Added mono Ogg Vorbis morph and water-glide audio (`sports_car_morph.ogg`, `sports_car_water_glide.ogg`) integrated into `ModSounds` and `SportsCarSoundController`.

## 1.9.34 - 2026-11-18

- Rebuilt the Klein Bottle GUI with high-contrast light surfaces and a dedicated tools dock beside the warehouse. Search, storage and all three workstations remain accessible together.
- Added a workstation slide transition, animated focus and hover states, inventory transfer pulses, and ready-output effects. Added a clear-search control and an explicit pickup-deposit toggle.

## 1.9.33 - 2026-11-18

- Redesigned the Klein Bottle terminal with a mint-accented graphite interface, quieter slots, and short transitions for search focus, drawer opening, and quick-deposit feedback.
- Changed the default Klein Bottle and skill-wheel keys to B and J. Moved the bundled phone's camera exit to Z and reader paging to [ and ]. Existing installations migrate the affected defaults where they collide.

## 1.9.32 - 2026-11-18

- **Gulf GT Sports Car (`universal_tool:gulf_gt_car`)**:
  - Added the two-seater Gulf GT Sports Car entity (`SportsCarEntity`), placement item (`SportsCarItem`), and 12-slot Universal Polymerizer recipe (`gulf_gt_car_polymerize`).
  - Implemented mouse-aim steering with A/D assist and reverse steering, handbrake drifting (`Space`), auto-recharging nitro boost (`Ctrl`, accelerated recharge while drifting), 1-block auto step climbing (`maxUpStep = 1.05F`), four-wheel terrain pitch/roll alignment, and proximity/mount-triggered automatic welcome doors (`doorOpenProgress` with door swing sound).
  - Added full 3D pre-baked entity & item mesh rendering (`SportsCarModelData`, `SportsCarRenderer`, `SportsCarItemRenderer`) with second-order spring suspension, steering/spinning wheels, linked steering wheel, smart auto headlights & brake/reverse taillights, translucent glass rendering, nitro exhaust plumes, drift/exhaust particles, 7-block third-person & cockpit camera framing, and speed/drift/nitro dynamic mono Ogg Vorbis audio (`SportsCarSoundController`).

## 1.9.31 - 2026-11-18

- Added two steerable rear nozzles to the All-Terrain Wheelchair. Hold jump to sustain lift for up to 0.8 seconds. The chair now floats and drives on lava. Removed the decorative yellow lever.
- Didi Daida now costs 32 emeralds in the MCPhone store. Two-minute orders cost 8 emeralds for one Naiwa or 20 for three. Each player can have one active order with one squad-wide ultimate.
- Existing single-player installations carry over. Server administrators can grant access to existing players with `/ut didi grant <player>`.

## 1.9.16 - 2026-11-13
- **Corridor Starfield Accents & Instant Wormhole Closure on Entry**:
  - Added a 3D twinkling cosmic starfield (`seedStarfield` + ongoing outer `END_ROD` / `ENCHANT` / `PORTAL` starlight particles) surrounding the transparent wormhole corridor without obstructing the forward destination vista.
  - Updated `WormholeEntity` so any non-spectator player entering an entrance wormhole (or the owner hitting the corridor exit) immediately collapses and discards the wormhole with a crisp particle burst instead of lingering.

## 1.9.15 - 2026-11-13
- **Live Server-Side Destination Vista Raycasting & Panoramic Corridor Exit**:
  - Added `LiveVistaSampler` (fast 3D DDA voxel raycaster capturing a live `112×63` 16:9 snapshot directly from the target `ServerLevel`, reflecting real-time blocks, water, sky/block lighting, and current day/night sky across dimensions) and `WormholeLiveVistaPayload` S2C sync into `PhotoManager.registerLiveVista`.
  - Updated `TeleportManager`, `WormholeEntity`, and `WormholeCorridorManager` so both the entrance wormhole and the corridor exit wormhole display the live-sampled destination view.
  - Enlarged the corridor exit wormhole (`rx=3.20, ry=2.25`) so the live destination vista fills the forward view inside the transparent corridor.

## 1.9.14 - 2026-11-13
- **Corridor Legacy Block Cleanup Fix**:
  - Expanded the air-clearing envelope in `WormholeCorridorManager.buildTransparentCorridor` (`z = -2..20, dx = -4..4, y = 60..70`) so any leftover `v1.9.12` deepslate/obsidian blocks behind the exit wormhole (`z = 16..18`) are completely removed.

## 1.9.13 - 2026-11-13
- **Larger Panoramic Wormhole Portal & Purely Transparent Transit Corridor**:
  - Enlarged `WormholeRenderer` aperture (`rx=1.35, ry=1.45` at entrance; `rx=2.15, ry=1.85` at corridor exit) and widened screenshot UV sampling (`rAper=0.86`, `±0.45` UV span) so the full destination vista is clearly visible without heavy cropping.
  - Replaced solid stone/obsidian tunnel walls in `WormholeCorridorManager` with a **purely transparent invisible-barrier corridor** (`Blocks.BARRIER` floor, guard rails, and ceiling + subtle floating `END_ROD` rings), keeping the entire view clear with the large destination-preview exit wormhole directly ahead.

## 1.9.12 - 2026-11-13
- **Wormhole Ring Missing-Texture Fix & Dedicated Enclosed Dimensional Corridor**:
  - Fixed `WormholeRenderer.VORTEX_TEXTURE` path from `textures/entity/stargate_portal_vortex.png` to `textures/block/stargate_portal_vortex.png`, eliminating the purple/black missing-texture checkerboard on the wormhole perimeter ring.
  - Decoupled `WormholeCorridorManager` (`universal_tool:wormhole_transit`) completely from `PrivateSpaceManager` / `StarryPocketChunkGenerator`, creating an independent void dimension (`FlatLevelSource` + `BuiltinDimensionTypes.END_EFFECTS`).
  - Rebuilt the transit corridor into an **enclosed dark deepslate & obsidian architectural tunnel** with a translucent `CYAN_STAINED_GLASS` / `TINTED_GLASS` walkway over glowing `SEA_LANTERN` & `CRYING_OBSIDIAN` under-floor, stepped octagonal cyan/amethyst energy arches (`z = 5, 10, 15`), soul-lantern & amethyst accents, and the destination-preview exit wormhole at the far end.

## 1.9.11 - 2026-11-13
- **Astral Void-Glass & Crystalline Cyan Klein Bottle GUI Overhaul**:
  - Redesigned `klein_terminal.png` (`tools/craft_ui_machines.py`) from the previous warm parchment/walnut theme into a dedicated **Astral Void-Glass & Crystalline Cyan** theme matching the Klein Bottle item aesthetic: deep cosmic-indigo frame, glowing cyan-aqua corner brackets & dividers, recessed void-glass slots, crystalline cyan scrollbar & soul-cyan furnace/button glyphs, and amethyst-rose lock indicator.
  - Updated `KleinScreen.java` label, button, search/anvil input well, and tooltip/border colors (`0xFFD8F6FF` ice-cyan text, astral-indigo beveled sort/direction buttons) to integrate cleanly with the dark crystalline terminal.

## 1.9.10 - 2026-11-13
- **32×32 Mod-Style Astral Tome Codex & Walkable Wormhole Corridor with Destination Vista**:
  - Upgraded `teleport_codex.png` (+ `.mcmeta`) to crisp **32×32 8-frame mod pixel art** matching the mod's beveled astral-indigo, brushed gold clasp, cream striated page block, and glowing cyan-amethyst core gem aesthetic while keeping the recognizable Minecraft bound-book silhouette.
  - Enhanced `WormholeRenderer` & `WormholeEntity` so wormholes display a **parallax destination vista** inside their oval aperture (`PhotoManager.getOrLoadTexture(photoId)` screenshot or dimension-themed horizon fallback) framed by a hollow swirling cosmic energy rim.
  - Added `WormholeCorridorManager` (`universal_tool:wormhole_transit`): stepping into an entrance wormhole transports the player into a **walkable ~14-block starlit spatial runway** with glowing energy rings, Speed II boost, and an exit wormhole ahead showing the destination vista; running through completes the teleport cleanly.

## 1.9.9 - 2026-11-13
- **Unified Heavy-Core Mechanical Base Form for All 6 Universal Tools**:
  - Replaced all 6 Base Universal Tool textures (`wooden`, `stone`, `iron`, `golden`, `diamond`, `netherite_universal_tool.png` + `.mcmeta`) with a cohesive 32×32 8-frame animated Heavy-Core/Mace mechanical morph-handle silhouette featuring wrapped grip, pommel energy node, four beveled armor brackets, pulsing solid diamond core crystal, and diagonal shimmer sweep.
  - Updated all 6 root item models (`*_universal_tool.json`) to `minecraft:item/handheld` while preserving all 5 vanilla tool overrides (`_pickaxe`, `_axe`, `_shovel`, `_hoe`, `_sword`).

## 1.9.8 - 2026-11-13
- **Vanilla-Book Teleport Codex & Immersive Walk-In Spatial Wormhole**:
  - Restyled `teleport_codex.png` (+ `.mcmeta`) directly from Minecraft 1.21.1's vanilla `book.png` silhouette with astral midnight-indigo leather cover, gold clasps, glowing cyan emblem, and diagonal shimmer glint.
  - Unlocked all 36 waypoint slots by default (`WaypointData.DEFAULT_CAPACITY = 36`, `ClientWaypointCache` default `36`), removing the spatial-page expansion requirement before teleporting/recording across all 3 volumes.
  - Replaced standing-still channel teleport with an immersive walk-in **Spatial Wormhole (`WormholeEntity` & `WormholeRenderer`)**: clicking Warp opens a floating, double-layered swirling cosmic wormhole ~2.3 blocks ahead of the player; walking inside teleports cleanly to the destination (supporting cross-dimension travel) and collapses the rift.

## 1.9.7 - 2026-11-13
- **All 6 Universal Tool Tiers Dynamic Morphing & Vanilla 1.21.1 Silhouettes + Glint Accents**:
  - Extended dynamic form switching (`universal_tool:tool_form`) from Netherite-only to all 6 Universal Tool tiers (`wooden`, `stone`, `iron`, `golden`, `diamond`, `netherite`).
  - Built all 30 functional morph textures (`_pickaxe`, `_axe`, `_shovel`, `_hoe`, `_sword` × 6 tiers) directly from authentic Minecraft 1.21.1 vanilla tool sprites overlaid with a diagonal energy shimmer sweep and pulsing tier-core collar gem (`.png` + `.png.mcmeta`).
  - Fixed block-tag priority in `NetheriteToolForm.detectForBlock` so `BlockTags.MINEABLE_WITH_HOE` / `BlockTags.LEAVES` take precedence over `BlockTags.SWORD_EFFICIENT`, ensuring mining leaves morphs into a Hoe rather than a Sword.

## 1.9.6 - 2026-11-13
- **Animated Morphing Netherite Universal Tool (`NetheriteToolForm` & 32×32 Animated Sprites)**:
  - Redesigned `netherite_universal_tool` with 6 high-detail 32×32 8-frame interpolated animated spritesheets (`.png` + `.png.mcmeta`): an arcane folding amethyst-core Base Instrument (`netherite_universal_tool`) plus 5 functional forms (`_pickaxe`, `_axe`, `_shovel`, `_hoe`, `_sword`) sharing unified Netherite-gold-amethyst styling.
  - Added typed `ModDataComponents.NETHERITE_TOOL_FORM`, `UniversalToolFormEvents`, `ItemProperties` predicate (`universal_tool:tool_form`), and `shouldCauseReequipAnimation` / `shouldCauseBlockBreakReset` overrides on `UniversalToolItem` so mining, chopping/stripping, digging/pathing, tilling/reaping, or attacking switches smoothly into the matching form and auto-folds back to Base after ~1.8s (`36` ticks) without hand bobbing or mining resets.

## 1.9.5 - 2026-11-13
- **Half-Base to Full-Stargate Unfolding Animation (`PersonalSpaceGateRenderer` & `PersonalSpaceGateBlockEntity`)**:
  - Kept closed/idle `PersonalSpaceGateBlock` as the compact 1-block mossy carved stone pedestal base (`HALF = LOWER` only).
  - When activated (`OPEN = true`), `PersonalSpaceGateRenderer` smoothly animates the 4 stone/moss/cyan-rune tiers (`TIER_1_PILLARS`, `TIER_2_LOWER_CURVE`, `TIER_3_UPPER_CURVE`, `TIER_4_CROWN`) rising and unfolding from inside the base into the complete 2.5-block pointed-oval Stargate arch over ~1.1 seconds, followed by the electric-cyan spiral vortex (`stargate_portal_vortex.png`) blooming at the center.
  - Added smooth retraction back into the pedestal base (`clientAnimTicks` unwinds cleanly) when closing the gate (`OPEN = false`), while preserving safe `UPPER`/`LOWER` cleanup guards in `PersonalSpaceGateBlock.onRemove`.

## 1.9.4 - 2026-11-13
- **Fix Missing Texture & Distorted Portal Quad on Stargate**:
  - Removed reference to nonexistent `personal_space_gate_energy.png` in `PersonalSpaceGateRenderer` that caused the purple-black missing texture checkerboard.
  - Replaced the rotated square quad with a 24-segment pointed-oval fan (`rx = 0.56`, `ry = 1.00`) using rotating UV coordinates on `stargate_portal_vortex.png`, eliminating corner clipping/shearing outside the arch and Z-fighting.
  - Added `PrivateSpaceManager.ensureUpperHalf` so pre-existing single-block `v1.9.2` gates in saved worlds automatically sprout their upper pointed-oval arch half when interacted with or opened.

## 1.9.3 - 2026-11-13
- **Two-Block Pointed-Oval Rune Stone Stargate & Swirling Cyan Vortex**:
  - Upgraded `PersonalSpaceGateBlock` (`HALF = DOUBLE_BLOCK_HALF`, `LOWER`/`UPPER`) and `PersonalGateGeometry` into a 2-block-tall (`~2.5` blocks visual height) pointed-oval carved & mossy stone arch with 4 glowing cyan rune cross-nodes and inner cyan energy veins (`stargate_arch.png`, `personal_space_gate_lower.json`, `personal_space_gate_upper.json`).
  - Left the arch doorway hollow in closed state (`OPEN = false`), and updated `PersonalSpaceGateRenderer` to fill the tall oval arch (`y = 0.25..2.38`) with a vibrant electric-cyan & azure swirling pixel-art spiral vortex (`stargate_portal_vortex.png`) + outer energy rim glow when `OPEN = true`.
  - Synced `OPEN` state across both `LOWER` and `UPPER` halves (`PrivateSpaceManager`), expanded `PersonalSpaceGateBlockEntity` walk-in detection (`y..y+2.30`), and delegated `UPPER` clicks/entries cleanly to `LOWER`.

## 1.9.2 - 2026-11-13
- **Single-Line Personal Space Cosmic Bar**:
  - Streamlined the cosmic entry bar on `PersonalSpaceScreen` to a single clean line (`✦ 进入个人空间 · 按玩家区分 · 潜行右键星空地板返回`), removing the `25×25` and mob-spawning text.
- **Sci-Fi Dynamic Star Gate Portal & Step-In Auto-Close**:
  - Selecting a destination on `PersonalSpaceScreen` now activates the `PersonalSpaceGateBlock` (`OPEN = true`, light level `14`) with a sci-fi `PersonalSpaceGateRenderer` displaying iris-expanding counter-rotating holographic energy rings, a swirling nebula event-horizon vortex, a pulsing quantum core flare, and swirling `END_ROD` particles.
  - Opened gates remove their center doorway collision (`PersonalGateGeometry.openCollisionShape`) so players can walk straight into the portal; stepping inside teleports the player to the configured realm or UUID-bound personal starry space and immediately closes the gate back to normal (`OPEN = false`).

## 1.9.1 - 2026-11-13
- **Cloud-Free Dense Starry Sky for Personal Starry Space (`StarryPocketDimensionEffects`)**:
  - Replaced default Overworld sky/clouds in `universal_tool:private_pocket/<uuid>` with custom `universal_tool:starry_pocket` `DimensionSpecialEffects`.
  - Completely suppressed clouds (`cloudLevel = Float.NaN` and `renderClouds` override) and rendered a deep midnight-indigo sky dome, smooth radial-falloff galactic nebula band, and 2,800 multi-tiered twinkling stars in starlight white, ice-cyan, soft violet, and pale gold.

## 1.9.0 - 2026-11-13
- **Eight Realm World Traits (`PrivateSpaceWorldTraits` & `PrivateSpaceWorldTraitHandler`)**:
  - Added distinct, documented environmental rules across all 8 realms: fixed day/night clocks (`6000L` eternal noon, `18000L` eternal starry night, `13000L` dusk, ambient skylight modulation), tailored weather (`ALWAYS_CLEAR`, `SNOW_FLURRY`), custom natural mob rosters and spawn multipliers (e.g., Superflat zero natural spawns while preserving spawners/eggs/breeding; Mushroom Wonderland mooshroom-only; Obsidian Forge `2.0x` Nether-tier spawns with zombification immunity; Amethyst Starfall passive/neutral roster + Night Vision), geological ore tables (0 ores in Superflat; `2.5x` Overworld ores incl. diamond/emerald/redstone in Amethyst Starfall; XP/lapis/redstone focus in Cherry Grove; emerald/coal/iron in Sky Islands; quartz/gold/ancient debris in Obsidian Forge), and ambient protections against griefing/explosions.
  - Updated `PersonalSpaceScreen` to replace generic terrain blurbs with crisp `世界特性 / World Traits` summaries and rich hover tooltips covering time/weather, natural spawns, underground ores, and environmental buffs.
- **Free Realm Switching & Password-Free Single-Page Personal Starry Space (`25×25`)**:
  - Removed Star Gate realm lock-in and all password hashing/gate UI (`ACTION_ENTER_REALM`, `ACTION_ENTER_PRIVATE_SPACE`): clicking any `PersonalSpaceGateBlock` lets any player freely switch between all 8 shared global exploration realms (`universal_tool:realm/<env>`) while preserving their pre-realm Overworld/origin return coordinate across realm jumps.
  - Replaced the secondary submenu with a single-click cosmic bar directly on `PersonalSpaceScreen` (`✦ 进入个人空间 · 25×25` / `按玩家区分 · 不自然刷怪 · 潜行右键星空地板返回`), routing each player to their own UUID-keyed dimension (`universal_tool:private_pocket/<uuid>`).
  - Implemented `StarryPocketChunkGenerator`, `StarryVoidBlock`, and `StarryVoidBlockEntityRenderer`: places a `25×25` open-sky `StarryVoidBlock` beveled indigo-slate masonry floor (`y=63`, `x,z ∈ [-12..12]`) layered with a smooth translucent scrolling starlight/nebula veil (`RenderType.entityTranslucent`) for high spatial legibility, enclosed by transparent `Blocks.BARRIER` perimeter bounds and cap (`x,z=±13`, `y=89`) with midnight sky light (`hasSkyLight=true`, `hasCeiling=false`). Excluded `StarryVoidBlock` from `BlockItem` and creative tabs; Shift + Right-Clicking any `StarryVoidBlock` returns the player to their saved pre-entry location.

## 1.8.0 - 2026-11-13
- **Reference-Aligned Universal Tools & Klein Bottle (`64×64`)**:
  - Redrew all 6 `{wooden,stone,iron,golden,diamond,netherite}_universal_tool.png` sprites at `64×64` following the upright symmetrical cross-halberd reference silhouette (top diamond-spade spadehead, left pickaxe head, right axe blade, central core gem, brown leather-wrapped grip, and bottom dagger pommel) and updated `models/item/*_universal_tool.json` display transforms for upright GUI framing.
  - Redrew `klein_bottle.png` at `64×64` as a translucent crystal-glass looping Klein Bottle with dark collared junctions, cyan crystal rivets, and a crystal-inlaid base ring.
- **Star Gate Real-World Previews & Live Base Snapshots**:
  - Enlarged `PersonalSpaceScreen`'s brass-framed viewport (`64px` high) and rendered crisp real Minecraft in-game panoramic screenshots (`textures/gui/previews/env_*.png`) for all 8 `PrivateSpaceEnvironment` realms, auto-captured via `PrivateIslandVisualTest` (`runIslandVisual`).
  - Added `PersonalSpaceSnapshotHandler` + `PhotoManager` base-snapshot persistence so visiting or managing an existing private dimension displays the player's own captured base scene first and cleanly falls back to the selected realm's real-world screenshot.

## 1.7.0 - 2026-11-13

- **Arcane Brass & Illuminated Parchment Overhaul (Create / Ars Nouveau Aesthetic)**:
  - **32×32 High-Craft Items & Skill Icons**: Upgraded all 17 items and 20 skill icons to `32×32` hand-crafted pixel art (`tools/craft_items_skills.py`) — reshaping the 6 Universal Tools into diagonal Paxel-Halberds, `klein_bottle` into a true 4D looping crystal bottle, `teleport_codex` into an isometric gilded grimoire, `teleport_scroll` into a wax-sealed diagonal scroll, `void_scrap` into a forged void-obsidian ingot chunk, and `counter_guard_aegis`/`counter_guard_ring` into `64×64` golden-brass ward runes.
  - **Architectural 3D Block Models & Clean Textures**: Rebuilt all 11 `models/block/*.json`, 4 block-item `gui` display transforms, and 8 block texture atlases (`tools/craft_blocks.py`) around Dark Basalt Slate, Warm Alabaster, Walnut Timber, Create-style Brushed Brass, Clear Crystal Glass, and Astral Teal/Amethyst focal cores (eliminating noisy checkerboard static). Updated `UniversalLotteryMachineRenderer` atlas swatches to match.
  - **Warm Parchment, Walnut & Brass UIs**: Upgraded all 7 UI screens (`PolymerizerScreen`, `PhoenixPodScreen`, `KleinScreen`, `UniversalLotteryMachineScreen`, `PersonalSpaceScreen` Star Gate + 8 `32×32` biome medallions, `CodexScreen`, `SkillScreen` & `SkillWheelScreen`) and JEI `PolymerizerRecipeCategory` to a unified illuminated parchment, walnut wood, and burnished brass workshop aesthetic.

## 1.6.0 - 2026-11-13

- **Vanilla+ Textures & UI Overhaul**:
  - Rebuilt all 7 UI screens (`PolymerizerScreen`, `PhoenixPodScreen`, `KleinScreen`, `UniversalLotteryMachineScreen`, `PersonalSpaceScreen` Star Gate GUI, `CodexScreen`, `SkillScreen` & `SkillWheelScreen`) and JEI `PolymerizerRecipeCategory` into classic Minecraft `#C6C6C6` beveled stone-gray panels, `#8B8B8B` recessed 18×18 slots, `#404040` headers, and authentic Vanilla `book.png` leather+parchment styling for `CodexScreen`.
  - Redrew all 8 Star Gate biome emblems (`environment_*.png`), all 20 skill icons (`textures/gui/skills/*.png`) at crisp `16×16` Vanilla status-effect / advancement pixel art, and `counter_guard_aegis.png` / `counter_guard_ring.png` as crisp pixelated ward rune textures.
  - Redrew all 17 item textures (`textures/item/*.png`) in Jappa 1.14+ pixel art (tinted outlines with zero `#000000` borders, classic oak/netherite handles, Vanilla glass/book/crystal palettes) and scaled `lightning_spear` to `32×32` 1× texel density with aligned model UVs.
  - Retextured all 5 blocks (`Universal Polymerizer`, `Universal Lottery Machine`, `Personal Space Gate`, `Personal Space Return`, `Operation Phoenix Pod`) around Vanilla End & Magic block materials (Purpur, End Stone Bricks, Obsidian/Crying Obsidian, Enchanting Table crimson/gold trim, Bookshelf wood, and Vanilla glass) while preserving 3D model geometry and UV layouts.

## 1.5.2 - 2026-11-13

- Fixed client crash when opening the creative inventory (`IllegalArgumentException: Itemstack 1 universal_tool:void_essence already exists in the tab's list`) by removing duplicate `VOID_ESSENCE` entries in `ModCreativeTabs`.

## 1.5.1 - 2026-11-13

- Merged remote `origin/main` (`1.5.0`) with local Void Essence polymerizing feature (`1.4.1`).

## 1.5.0 - 2026-11-13

- **Architecture & Dedicated Server Side Isolation**:
  - Removed all client-only imports and registrations from `UniversalToolMod`, `network/` payload classes, `space/`, and `TeleportCodexItem`, routing client screens, BERs, entity renderers, keybinds, and S2C payload handlers through dedicated client-only entrypoints.
  - Introduced `AbstractDoubleEntityBlock` for shared two-block-tall `BaseEntityBlock` placement and cleanup across Operation Phoenix Pod and Universal Lottery Machine.
  - Decomposed `SkillManager` into focused combat, active-skill execution, and passive-skill handlers, and internationalized skill stat formatting.
- **Configuration, Data Components & Attachments**:
  - Added NeoForge `COMMON` config (`ModConfigs`) externalizing `polymerizerDefaultTicks`, `teleportCooldownTicks`, `phoenixCultivationTicks`, `skillXpMultiplier`, and `lotteryBaseScrapChance`.
  - Added typed Minecraft 1.21.1 `ModDataComponents` (`DNA_OWNER`, `NUTRIENT_SNAPSHOT`, `TELEPORT_TARGET`) with backward-compatible `DataComponents.CUSTOM_DATA` fallback readers.
  - Consolidated attachment registration in `ModAttachments` while preserving existing `waypoint_data` and `skill_data` keys.
- **Pocket Realm 8 Fantastical Landscapes**:
  - Replaced the 5 mundane overworld-style realm presets with 5 fantastical landscapes alongside upgraded Starlit Superflat, Celestial Sky Archipelago, and Mystic Cherry Sanctuary: `AMETHYST_STARFALL`, `SCULK_SANCTUM`, `MUSHROOM_WONDERLAND`, `OBSIDIAN_FORGE`, and `AURORA_GLACIER`.
  - Added tapered stalactite undersides, multi-layered geological strata, and bounded procedural landmarks, with seamless legacy ID mapping (`plains`, `desert`, `snowy_plains`, `birch_forest`, `savanna`).
- **Art, Models & Rendering Overhaul**:
  - Unified world assets around a 1× texel-density Arcane-Tech style, upgrading the Universal Polymerizer to a 3D astrolabe pedestal with BER item/particle rendering, fixing Phoenix Pod 180° door orientation, and resolving Lottery Machine UV/emissive/die-face rendering issues.
- **Klein Hyper-Storage, Void Lottery & JEI Integration**:
  - Documented Klein Bottle Hyper-Storage and Universal Lottery Machine (Void Dice, Cosmic Roulette, Void Slots 777) in `README.md` and `GLOSSARY.md`, and added JEI `addIngredientInfo` pages for core machines and items.
  - Hardened server authority and persistence: permission checks on `/ut` commands, server-authoritative waypoint coordinates and item checks, path-traversal sanitization in `PhotoManager`, missing block loot tables, container/extra-reward drops on block removal, Phoenix rebirth inventory duplication prevention, Polymerizer recipe duration/crafting remainder support, and Klein scroll-wheel inventory insert.

## 1.4.1 - 2026-11-07

- Added Void Essence (`void_essence`), an Epic material forged from condensed void energy.
- Added Universal Polymerizer recipe: 12 Void Scraps (`void_scrap`) fill the 12 outer clock slots to synthesize 1 Void Essence in the center slot.
- Added Void Essence to the Universal Tool creative tab and ingredients list.
- Integrated Void Essence evaluation into the Universal Lottery Machine (1800 points, Epic tier).
- Added GameTests for the 12-scrap Void Essence polymerizing recipe.

## 1.3.1 - 2026-10-31

- Added the Universal Repair Material and its anvil integration, including the Klein Bottle anvil.
- Redesigned the Universal Polymerizer interface and moved the Klein Bottle recipe to it.
- Updated Counter Guard projectile deflection, damage reflection, and first-person effects.

## 1.3.0 - 2026-10-31

- Added Shift-right-click container transfer while holding a Klein Bottle, including NeoForge item-handler storage from modded chest-like containers.
- Added optional pickup auto-deposit for item types already stocked in the terminal; it is per-player and off by default.
- Replaced the terminal's clear-search action button with the auto-deposit toggle while preserving right-click search clearing; enabled buttons now change color without shifting their icons.
- Added persistent preference storage, protocol 14, and GameTests for held-item container transfer, processing-container exclusion, matching rules, and preference persistence.

## 1.2.7 - 2026-10-27

- Show a lock badge on every inventory item that cannot enter Klein Bottle storage, including other Klein Bottles.
- Apply paged warehouse snapshots only after their final page, preserving scrolling beyond 64 entries without snapback.
- Preserve the scrollbar grab offset and support accumulated wheel scrolling through large warehouses.
- Updated the network protocol; multiplayer clients and servers must use the matching mod version.

## 1.2.6 - 2026-10-27

- Moved the Klein Bottle recipe to the Universal Polymerizer: four purpur blocks, four glass, three ender pearls, and one nether star.
- Added the Universal Repair Material, crafted in the Universal Polymerizer from netherite scraps, amethyst shards, and diamonds.
- An anvil repair consumes one material and three experience levels to restore any damageable item to full durability while preserving components.
- Added GameTests for full repair, component preservation, and invalid-item rejection.

## 1.2.5 - 2026-10-26

- Gravity Pull radius doubled to 28/32/36 blocks and duration now scales from 5s to 11s across ranks.
- Frozen Pull now scales freeze duration with rank: 5s at Lv.1, 6s at Lv.2, and 7s at Lv.3 (+1s per rank).
- Scaled Lightning Spear uncharged base damage and fully-charged damage bonus by 2.5x.
- Replaced Berserk damage resistance effect with a 20% base attack damage bonus for 10 seconds.
- Reworked Counter Guard visuals: removed circling shield quads and internal shield item; added a translucent hexagonal aegis dome, orbiting gyro rings, and explosive parry shockwaves.

## 1.2.4 - 2026-10-20

- Hid the internal Lightning Spear model item from the creative inventory; skill projectile rendering remains available.

## 1.2.3 - 2026-10-19

- Added Diamond Bulwark and Damage Clamp passives, with equipment-aware armor and final-hit damage limits.
- Raised the level cap to 50 and balanced the full skill budget at 68 points.
- Quadrupled linear XP requirements; migrated old progress without reclaiming earned skills or points.
- Level-up notifications display the current skill-menu key binding and play a sound.

## 1.2.2 - 2026-10-19

- Counter Guard now renders three independently orbiting ward shields around the protected player.
- Added a readable cast expansion, hit-trigger contract/flash/expiry animation, and cleanup on unequip, respec, logout, respawn, and dimension changes.
- Added a dedicated 78-cube Blockbench shield and synchronized its visual lifecycle to nearby clients.

## 1.2.1 - 2026-10-18

- Oppressive Field scales from a 12-block radius and 6-second Weakness III to 18 blocks and 10 seconds.
- Air Step now propels continuously while held, with a 1/2/3-second rank-based limit and cooldown on completion.
- Ore Sight now synchronizes private ore positions and draws through-wall HUD markers, with expiry and dimension cleanup.


## 1.2.0 - 2026-10-18

- Added persistent player growth with independent XP, levels, and skill points.
- Added the skill management screen and in-game active-skill wheel.
- Added nine actives: Counter Guard, Gravity Well, Frozen Pull, Oppressive Field, Ore Sight, Air Step, Lightning Spear, Berserk, and Ranged Barrage.
- Added four passives: Battle Momentum, Focused Mining, Last Stand, and Survival Flow, all driven by the equipped loadout.
- Added three levels per skill, active-slot expansion from 3 to 6 at level 10, and 4 passive slots.
- Added a level-40 cap whose total point budget exactly covers learning every skill to max level.
- Added per-level milestone points, so a single large award cannot skip a level-5 or level-10 bonus.
- Added wall-clock cooldowns that keep recovering while the server is offline.
- Added charged casting with press/release input, charge cancellation, and a short cancel lockout.
- Added loadout slot removal and replacement from the management screen.
- Added combat XP sharing within 32 blocks, crop-harvest XP, and an exclusion for blocks the player placed.
- Added target protection limited to hostile creatures, plus players and pets under server PVP rules.
- Added GameTests for milestone points, budget balance, respec refunds, offline cooldown recovery, slot expansion, and placed-then-mined exclusion.
- Gravity Well uses a 12-block base radius, growing to 16 with skill level.
- Reworked the skill management screen into a responsive game UI: a three-column desktop layout and compact Skills/Details/Loadout pages for small GUI-scaled windows.
- Added 13 deterministic pixel-art skill icons and a reusable visual style layer.
- Reworked the in-game HUD indicator into a compact 22x22 hotbar companion slot on the right side of slot 9, displaying skill icon, cooldown overlay, cast keybind, and charge bar.
- Refactored Frozen Pull targeting to acquire living entities directly under the crosshair ray, pulling them 2.2 blocks in front of the player with 5-second freeze stun and frost particles.
- Fixed repetitive freezing overriding Mob NoAI state, preventing AI from being permanently lost.
- Blocked frozen players from casting active skills or releasing charges.
- Protected the player's own pets from friendly skill targeting during PVP.
- Reinstated entity protections in canAffect: ArmorStands, Villagers, and non-hostile IronGolems are immune to offensive skills.
- Attributed direct caster source to Lightning Spear and Ranged Barrage via indirectMagic, ensuring kills award growth XP, discoveries, and combat stats.
- Fixed Battle Momentum to properly process and enhance damage against enemy players during PVP.
- Prevented player-placed blocks from granting mining XP even after 6000 ticks.
- Fixed combat XP distribution to strictly calculate true 32-block spherical distance instead of cubic AABB.
- Fixed respec (RESET) to preserve unrelated potion or beacon Haste effects.
- Fixed SkillScreen dialog dimensions at 320px minimum GUI width to prevent negative offset coordinates.
