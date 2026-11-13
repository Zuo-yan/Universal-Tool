# 万能修复材料验收 - 2026-10-27

## 已实现

- 新增 `universal_tool:universal_repair_material`，中英文名称分别为“万能修复材料”和 “Universal Repair Material”。
- 万能聚合台配方：4 下界合金碎片 + 4 紫水晶碎片 + 4 钻石，聚合时间 40 tick，产出 4 份材料。
- 铁砧规则：左侧为任何具有 `MAX_DAMAGE` 与 `DAMAGE` 的可损坏物品且已受损时，右侧 1 份万能修复材料可将物品恢复为满耐久。
- 修复输出是原物品的完整组件副本，附魔、自定义名称、Lore、盔甲纹饰和模组组件均保留；铁砧改名仍可同时生效。
- 铁砧固定消耗 3 级经验；满耐久物品、不可破坏物品、无耐久物品和错误右槽材料均不会走该自定义路径。
- 物品已加入模组创造模式标签，提供使用说明 Tooltip、16×16 RGBA 贴图和物品模型。

## 验证结果

- `.\gradlew.bat compileJava`：通过。
- `.\gradlew.bat build`：通过，生成 `build/libs/universal_tool-1.2.6.jar`。
- `.\gradlew.bat runIslandGameTest`：27/27 必需测试通过，耗时 20.55 秒。
- 新增 3 项 GameTest：
  - 任意受损物品修复至满耐久并保留附魔和自定义名称；
  - 满耐久物品与不可破坏物品被拒绝；
  - 万能聚合台 `4+4+4` 配方实际匹配并产出 4 份万能修复材料。
- `.\gradlew.bat runClient`：客户端启动、资源包重载和模型/贴图图集构建成功，未发现本功能相关的缺失资源错误。
- JSON 解析通过，贴图尺寸与通道检查为 16×16 RGBA。
- 最终 JAR 已检查，包含物品类、铁砧处理器、测试类、模型、贴图和配方资源。

## 复现

```powershell
.\gradlew.bat build
.\gradlew.bat runIslandGameTest
.\gradlew.bat runClient
```

![万能修复材料像素预览](previews/universal_repair_material_preview.png)

## 说明

客户端验证覆盖启动和资源加载；未在真实游戏中手动点击铁砧完成一次 UI 交易。铁砧行为、经验成本、材料消耗、数据保留与配方匹配均由服务端 GameTest 覆盖。
