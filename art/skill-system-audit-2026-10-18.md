# 技能系统代码审查（2026-10-18）

结论：技能系统已经接入客户端输入、网络注册、玩家附件与服务端事件，13 项技能都有实际处理分支；但目前不能验收为“实现完整、没有 bug”。本次在 NeoForge GameTest 环境复现 9 项逻辑缺陷，另有小窗口布局的静态问题。

审查期间 SkillManager.java 发生并行修改，已保留并重新核对。最后验证的源码快照 SHA-256：a74909025fdb0138e4a191cceed9bf09987dfa5f556b49f2468b4f40fc55aeed。初始和最后快照分别保存在 build/skill-audit/SkillManager.audited.java 和 SkillManager.latest-audited.java。

审查对象：当前 main 工作区（包含原有未提交修改），Universal Tool 1.2.0，Minecraft 1.21.1，NeoForge 21.1.251。未修改产品 Java 源码、资源、版本号或现有构建配置。隔离验证通过临时 Gradle init script 注入 build/skill-audit/src；普通构建不会加载该目录。

## 已复现缺陷

### 1. [P1] 重复冻结后怪物 AI 无法恢复

位置：[SkillManager.java:338](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:338)、[SkillManager.java:346](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:346)、[SkillManager.java:382](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:382)。

对同一怪物在冻结到期前再次施加冻结，第二次读取到的 isNoAi() 已经是 true，覆盖了首次保存的 false。到期恢复时又写入 true，怪物持续没有 AI。多人接力施放即可触发。

运行验证：连续调用两次 freeze，再把 tickFrozen 推进到到期时间，怪物 isNoAi() 仍为 true；单次冻结恢复 AI 的对照通过。

修复方向：续冻只延长截止时间，保留第一次进入冻结前的 AI 状态。还需处理卸载和停服：原版 Mob 会保存 NoAI，而 FROZEN 只存内存；该保存/重载风险由本地 Minecraft 源码确认，未另做重启实验。

### 2. [P1] 冻结玩家仍能施放主动技能

位置：[SkillManager.java:183](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:183)、[SkillManager.java:334](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:334)、[SkillManager.java:353](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:353)。

冻结只给玩家添加减速、虚弱、挖掘疲劳，tickFrozen 的行动处理仅作用于 Mob。castSelected 和 releaseCharge 均不检查冻结状态，因此与“无法移动、攻击或使用能力”的说明不符。

运行验证：冻结一个已装配狂战爆发的 ServerPlayer，再调用实际施放入口，仍获得力量效果；未冻结玩家施放成功的对照通过。没有执行真实客户端移动、弓弩射击、物品交互验收，不把这些写成已复现结果。

修复方向：统一服务端冻结状态检查，覆盖技能开始/释放、攻击与交互等入口，并补玩家移动和到期恢复验证。

### 3. [P1] 允许 PVP 时，自己的宠物会被当作敌对目标

位置：[SkillManager.java:578](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:578)。

宠物分支只检查 PVP 开关和 source.isAlliedTo(owner)，没有排除 owner == source。无队伍玩家对自己的 isAlliedTo 判断也是 false，因此会允许伤害、拉扯和冻结自己的宠物。

运行验证：无队伍玩家、其主人引用为该玩家的 Wolf、PVP=true，canAffect 返回 true。测试覆写 Wolf.getOwner() 来提供确定的在线主人引用；目标判断代码保持原样。

修复方向：先排除自己拥有的宠物，再判断队伍关系与服务器 PVP 规则；补主人离线情形。

### 4. [P1] 中立生物和展示实体的保护被放开

位置：[SkillManager.java:586](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:586)。

审查期间该方法的默认返回值从 target instanceof Enemy 变成了 true，README 仍明确声明村民、傀儡、动物和展示实体不受影响。现在所有通过前置基本过滤的其他 LivingEntity 都能进入技能目标列表，影响拉扯、冻结和直接伤害。

隔离运行验证：Cow、Villager、IronGolem、ArmorStand 四项均被错误允许为目标，日志逐项记录。

修复方向：恢复敌对目标默认限制，明确普通动物、村民、傀儡、展示实体与宠物的独立保护规则。

### 5. [P2] 雷霆之矛、万箭齐发的伤害没有施法者归属

位置：[SkillManager.java:470](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:470)、[SkillManager.java:506](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:506)、[SkillManager.java:695](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:695)。

两项技能使用 damageSources().magic()，本地原版代码确认该伤害源没有攻击实体。成长和解锁事件只接受 getSource().getEntity() 为 ServerPlayer 的伤害，所以技能直接击杀不会经过玩家击杀奖励路径，技能输出也不正常参与玩家连击与战斗状态记录。

运行验证：雷霆之矛实际命中并杀死低血僵尸后，没有获得预期成长经验/敌对击杀发现；普通玩家攻击击杀同时能获得经验和解锁资格的对照通过。万箭齐发的同一问题由共享伤害源写法静态确认，未另做击杀实验。

修复方向：使用包含施法者的魔法伤害源，并验证经验、击杀计数、战斗状态和反击护体。

### 6. [P2] 战意律动对玩家目标不增伤

位置：[SkillManager.java:768](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:768)。

onIncomingDamage 对 ServerPlayer 受害者执行防御逻辑后直接 return，导致后面的攻击者 applyMomentum 永远不处理 PVP 伤害。

运行验证：同样的三级被动、连击状态和 10 点输入伤害，玩家目标没有增伤；怪物目标增伤的对照通过。

修复方向：受害者的防御处理与攻击者的增伤处理应能够在同一事件内都执行，并规定结算次序。

### 7. [P2] 自己放置的方块超过 6000 tick 后重新给挖矿经验

位置：[SkillManager.java:943](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:943)、[SkillManager.java:956](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:956)。

放置记录判定有 6000 tick 时效，正常 TPS 下约 5 分钟。超过这个时间，再挖自己放置的石头就会获得成长，违背 README 的排除规则。记录仅在静态 Map 中，服务器重启也会遗失；重启情形为静态确认。

运行验证：向实际放置记录写入 6001 tick 前的时间，再调用真实挖掘事件入口，错误获得经验。自然石头正常获得 1 点经验的对照通过。原有测试只验证刚放下立即挖掉，漏掉超时情形。

修复方向：使用符合规则的持久放置来源记录；若设计确实要过期，必须先调整规则并明确刷经验边界。

### 8. [P2] 战斗经验范围按立方体筛选，超出 32 格的玩家也参与分配

位置：[SkillManager.java:748](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:748)。

使用目标包围盒 inflate(32) 的 AABB.contains，没有做三维距离平方判断。死亡点偏移 (31,0,31) 的玩家实际约 43.84 格，仍会分走经验，并稀释近处玩家所得。

运行验证：通过真实击杀结算处理器，给距离约 43.84 格的玩家错误分配成长经验。测试期间只短暂加入该测试世界玩家列表，并在 finally 内移除。

修复方向：AABB 只用于粗筛，之后按死亡点 distanceToSqr <= 32*32 过滤。另需验证大量玩家时 Math.max(1, xp / count) 是否会凭空增加总经验；这个总量边界本轮未单独运行。

### 9. [P2] 免费洗点会删除无关的急迫效果

位置：[SkillManager.java:635](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/skill/SkillManager.java:635)。

clearAllSkillEffects 无条件 removeEffect(DIG_SPEED)。即使玩家没有学习专注采掘，已有的信标或药水急迫也会被删掉，与另一处“不删除外部急迫”的注释不一致。

运行验证：给未学习相关被动的玩家添加 600 tick 的急迫 II，落地执行 RESET 后效果消失。

修复方向：只停止刷新技能赋予的短时效果，或追踪/恢复原有效果，避免误删其他来源。

## 已确认接通的部分

- SkillAttachments 在模组初始化时注册，提供 NBT 序列化和 copyOnDeath；登录、重生、切维度路径有同步处理。
- 技能请求、装配、选择、施放、取消蓄力和服务端快照包均注册，处理器把客户端意图交给服务端验证。
- 9 项主动均有施放实现；4 项被动分别接入连击、急迫、低血减伤、脱战恢复逻辑。
- 48 点总预算与全部技能学满费用匹配，经验跨多级里程碑、洗点返还、冷却存取和槽位扩展有现有测试覆盖。

这说明功能确实有代码和调用链，但不能据此认定所有功能满足说明。

## 额外静态问题和验收边界

SkillScreen.init 把面板宽度最低限制为 380；GUI 逻辑宽 320 时会得到 left=-30。当前实现始终绘制三栏，没有 README 声称的窄屏“技能/详情/装配”分页。位置：[SkillScreen.java:49](A:/Game/Code/Minecraft-Mod/万能工具/src/main/java/org/gwfx/universaltool/client/SkillScreen.java:49)。这是代码层面可计算的问题，本轮未启动真人客户端验收布局。

轮盘鼠标捕获、扇区选择、矿脉感知在岩石后是否可见、多客户端真实网络、死亡后保存/重连、真实服务器重启都未完成交互验收。未宣称无 bug，也未把构建成功等同于功能验收。

混合对照运行额外出现一次 returnGateProtectionAndPrivateRespawn 失败，随后测试服务器在 ChunkMap.scheduleUnload 保存阶段持续运行。首次原有 17 项测试全部通过，第一次加入缺陷探针的运行也未出现该私人空间失败。本轮记录该独立观察，不判断其是否由测试组合干扰或项目本身时序问题导致。保存了 mixed-controls-probes.log 和 mixed-run-shutdown-threads.txt，并仅终止核实为本次 build/skill-audit/world-run 的测试 JVM。最终技能验证使用另外的独立世界，不注册私人空间测试。

## 验证与复现

1. 普通构建：.\gradlew.bat --console=plain build。
2. 原有测试：.\gradlew.bat --console=plain runIslandGameTest。首次 17/17 通过，其中技能 6 项。
3. 隔离技能检查：.\gradlew.bat --console=plain -I build/skill-audit/audit.init.gradle runIslandGameTest。只通过临时 init script 引入本次验证代码。
4. 缺陷探针按预期正确行为断言，因此 9 项 FAIL 表示 9 个已复现产品问题；不是编译失败或测试夹具异常。另有 6 项正常路径断言作为对照。
5. 验证后普通 build 恢复正常产物，检查 jar 和编译输出不含审查类。

证据目录：[build/skill-audit](A:/Game/Code/Minecraft-Mod/万能工具/build/skill-audit)。主要文件为 build.log、gametest.log、probes.log、probe-results.txt、probe-results.txt.controls、restore-build.log，以及 src 下的可审查复现代码。

最终结果：独立世界只注册本次 2 个 GameTest；正常路径对照方法通过，包含 6 项通过断言；契约验证方法内 9 项检查全部复现预期行为不满足，因此该方法失败、任务退出码为 1。没有夹具 ERROR。牛、村民、铁傀儡、盔甲架全部被错误允许为目标。测试服务器正常保存退出。随后普通构建成功（退出码 0），已检查编译输出和 universal_tool-1.2.0.jar 不含审查类。收尾时 SkillManager.java 与报告源码快照哈希一致。

最终日志：isolated-probes.log、probe-results.txt、probe-results.txt.controls、final-build.log。首次和混合运行日志保留作独立证据。本次创建的已结束验证终端已关闭。
