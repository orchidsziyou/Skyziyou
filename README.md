# Skyziyou

[English](./README_EN.md) | 简体中文

> 本模组为 AI 辅助生成，如有介意请勿使用。

本模组为 Minecraft Fabric 端多功能辅助模组（主要用于 Hypixel SkyBlock）：按住一个自定义按键即可在屏幕中央打开一个 8 方位的指令轮盘，鼠标向某个方向滑动选中扇区，然后左键点击（或松开按键）即可快速执行预设指令或发送聊天消息；此外还包含自定义快捷键、自定义命令别名、按聊天频道自动触发动作的自定义聊天规则、进入冰川矿井时自动向队伍频道播报尸体数量与类别、私信自动邀请等独立功能。支持 **Minecraft 26.1 与 26.2**，两个版本由同一份源码构建、分别发布对应 jar。

## 界面预览

**游戏内轮盘：**

![游戏内轮盘](UI.png)

**设置界面 - 实体高亮：**

![实体高亮设置页](settingpage.png)

**设置界面 - 自定义聊天规则：**

![自定义聊天规则设置页](settingpage2.png)

## 功能特性

- **8 方位轮盘**：上、右上、右、右下、下、左下、左、左上，每个方位可独立配置内容
- **双执行模式**：
  - 以 `/` 开头的条目作为**指令**执行
  - 其他内容作为**聊天消息**原样发送
- **两种触发方式**：左键点击执行，或开启 "Execute On Release" 后松开触发键直接执行
- **智能交互**：
  - 轮盘打开时自动**锁定视角**，移动鼠标选扇区不会转动镜头
  - 轮盘打开期间**吞掉原版鼠标点击**，不会误攻击/误放方块
  - 右键随时取消关闭；超时（可配置）自动关闭
- **内置分类设置界面**：游戏内输入 `/skyziyou` 或 `/sz` 打开，深色主题 + 左侧分类侧栏（风格参考 SkyHanni），开关为绿色 ✓ / 红色 ✕，各功能位于独立子页面，所有改动即时生效、Esc/完成时自动保存；侧栏底部可在 **English / 中文** 之间切换界面语言（仅设置界面，发出的聊天/指令内容不变）
- **General（综述页）**：显示当前版本号（由 Fabric Loader 按 mod id 读取）、GitHub 链接（按钮一键用系统浏览器打开），以及手动"检查更新"——点按钮向 GitHub 查询最新 release 并在页面提示"已是最新 / 发现新版本"，**只查询、不自动下载安装**，确认有新版后自行打开 Releases 页面下载
- **自定义快捷键**：任意按键（含鼠标键，如小键盘 2）可直接绑定一条指令或消息，按下立即执行，在 "Custom Shortcuts" 子页面配置；每条可单独启用/停用，按键冲突会标红提示
- **Custom Command Aliases（自定义命令别名）**：把你在聊天框手输的命令改写成另一条命令或一条聊天消息，在 "Command Aliases" 子页面配置，每行两个框
  - 第一框：你输入的命令（带不带 `/` 均可，如 `/home`）；第二框：实际执行的内容（`/` 开头为命令，否则为聊天消息）
  - **仅精确匹配**（忽略大小写）：输入别名带额外参数时不匹配，原命令照常发出
  - 总开关、每条单独启用/停用、改写时本地日志提示（可关）
  - 全新安装首次启动自带一条示例别名 `/pl` → `/p leave`（可删除，仅在无配置文件时生成一次，不会重复添加）
- **Screen Alerts（屏幕提醒）**：聊天中出现指定文字时，在屏幕中上方弹出大号居中横幅提示 4 秒，在 "Screen Alerts" 子页面配置，每行两个框
  - 第一框：要检测的聊天文字（忽略大小写的包含匹配）；第二框：屏幕上显示的内容（留空则显示检测文字本身）；行末可限定只监听某一频道（任意 / 队伍 / 公会 / 全局 / 私信 / 系统，默认任意）
  - 显示文字支持 § 颜色码和 `[player]`（发送者昵称）、`[me]`（自己昵称）占位符
  - 横幅字号可在设置页用 −/+ 调整（1.0–5.0 倍，步进 0.25），页面底部有实际大小的实时预览
  - 相同内容只刷新计时不重复堆叠，多条不同提醒垂直排列（最多 5 条）；纯客户端 HUD 绘制，不发包
- **Entity Highlights（实体高亮）**：在 "Entity Highlights" 子页面输入生物名字，世界中所有显示名包含该文字的生物（怪物/动物/NPC，忽略大小写）会获得原版发光轮廓，**隔墙可见**，便于找人找怪
  - 每行：开关 → 生物名字 → 轮廓颜色（点击循环 15 种原版颜色：红、金、黄、绿、青、蓝、粉、白等）→ 删除；多条规则命中同一生物时按列表顺序取第一条
  - 同时识别生物**自带名字**和**头顶全息文字**（Hypixel 的 NPC/怪名字通常挂在隐形盔甲架上，如 Hub 铁匠 Blacksmith），匹配后发光的是生物本体
  - 纯原版机制实现（直接置位实体共享发光位 + 客户端记分板队伍颜色），无渲染 Mixin、不发包；自动跳过盔甲架和玩家自己
  - 注：客户端不能用 `setGlowingTag` 点亮实体（该方法在物理客户端是空操作），通过一个仅含 1 个方法的 Mixin Invoker（`EntityAccessor`）调用 protected 的 `setSharedFlag(6, true)`
  - 采用**生成时匹配 + 周期对账**：实体进入视野立即检测，另有每 2 秒一次的轻量全量对账兜底（全息名字晚到也会在 2 秒内自愈），稳态每秒仅 0.5 次世界遍历；平时每 tick 只维护已高亮的少量目标
- **Custom Chat Rules（自定义聊天规则）**：按聊天频道（任意 / 队伍 / 公会 / 全局 / 私信 / 系统）匹配收到的聊天行，自动发送回复或执行指令，在 "Custom Chat Rules" 子页面配置
  - 匹配方式：包含匹配或完整正则匹配，每条规则可用 **Aa** 按钮切换是否忽略大小写（默认忽略；非法正则只提示一次并自动停用）
  - 动作：以 `/` 开头作为指令执行，否则作为聊天消息发送
  - 占位符：`[player]` = 发送者昵称、`[me]` = 自己的昵称；正则模式额外支持 `$1`–`$9` 捕获组替换
  - 频道前缀按服务器下发的 § 颜色码识别，玩家无法通过打字伪造队伍 / 公会消息触发规则
  - 每条规则 3 秒冷却，所有动作经全局队列按 1 秒一条限速发送（断线自动清空），可限定仅 Hypixel 生效
  - 全新安装首次启动自带两条公会频道示例规则：`@[me] !i` → `/p [player]`、`@[me] boop` → `/boop [player]`（正则匹配，可自由修改或删除，仅生成一次）
- **Chat Sounds（聊天音效）**：和自定义聊天规则一样按聊天内容匹配，但命中后**只在本地播放音效**（不发包、不发消息），在 "Chat Sounds" 子页面配置
  - 内置 23 种原版音效（经验球"叮"、音符盒、猫叫、狗叫、升级、铃铛、末影龙等），无需资源包
  - **支持自定义音效**：在音效选择面板点"＋ 添加自定义音效"，选择本地 **.ogg（Ogg Vorbis）或 .mp3** 文件（≤ 10 MB、≤ 60 秒），起个名字即可；MP3 在导入时自动解码，文件原样保存在 `config/skyziyou/sounds/`，无需重启/重载资源；可重命名、删除
  - 点击每行的音效按钮打开**音效选择面板**（原版 + 自定义全部列出，可滚动，列表里直接点 ▶ 试听）；每行音效按钮旁的 ▶ 按当前连播次数试听
  - 列表第一行是固定的**角色死亡**触发，双信号判定：原版死亡屏幕（任意服务器/单机）**或** SkyBlock 的 `☠ You …` 死亡消息（Hypixel 不弹原版死亡屏，地牢变鬼魂也靠它），3 秒内两个信号只响一次；与语言无关，默认关闭，音效与连播次数可自行配置
  - 每行：开关 → 匹配文本 → 音效 → ▶ → 连播次数（×1–×5，间隔约 0.25 秒）→ 频道（ANY/PC/GC/AC/PM/SYS）→ Aa 大小写 → 包含/正则；匹配规则、`[me]` 占位符、正则保护与聊天规则完全一致，每条规则 2 秒冷却；页面顶部 **Log** 开关（默认开启）可在本地聊天栏输出命中日志
- **Mineshaft Corpse Report（矿井尸体播报）**：进入 Glacial Tunnels 矿井后，读取 Tab 列表中的 "Frozen Corpses" 区块，自动向队伍频道发送一次各类尸体的数量，在 "Mineshaft Corpses" 子页面配置
  - 详细格式（默认）：`/pc Frozen Corpses (4): 2x Lapis, 1x Umber, 1x Vanguard`；简略格式：`/pc Corpses (4): 2l 1u 1vg`（l/t/u/vg 分别为 Lapis/Tungsten/Umber/Vanguard，只列出该矿井存在的类别）
  - 数据直接来自 Tab 文本，**不扫描实体、不受视距影响**，进入矿井加载完成后约 1 秒内发送；每个矿井只播报一次，离开后再次进入才会重新播报
  - 总开关、仅 Hypixel 生效、详细/简略格式选项（总开关默认开启，默认详细格式）
- **Auto Party Invite**：收到内容为 `!invite` 的 Hypixel 私信时自动执行 `/p <玩家>`（同玩家 60 秒冷却、命令间隔 1 秒排队）

## 环境要求

| 项目 | 版本 |
|---|---|
| Minecraft | **26.1.x 或 26.2**（请下载与游戏版本对应的 jar） |
| Fabric Loader | >= 0.19.0 |
| Fabric API | 与游戏版本对应的最新版 |
| Fabric Language Kotlin | >= 1.13.0 |
| Java | >= 25 |

仅客户端模组，服务端无需安装。

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/installer/)
2. 下载 [Fabric API](https://modrinth.com/mod/fabric-api) 与 [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)，放入 `mods` 文件夹
3. 从 [Releases](../../releases) 页面下载**与你的 Minecraft 版本对应**的模组 jar（文件名含 `mc26.1` 或 `mc26.2`），放入 `mods` 文件夹
4. 启动游戏

## 使用方法

1. 游戏内输入 `/skyziyou`（简写 `/sz`）打开设置界面，从左侧侧栏切换各功能分类
2. 进入 **Command Wheel** 子页面，点击 **Wheel Keybind** 按钮后按下任意键（或鼠标键）绑定触发键
3. 在右侧 8 个输入框中填入各方位要执行的指令或消息（留空表示该方位不可用）
4. 按住触发键打开轮盘 → 鼠标滑向目标方位 → 左键点击（或松开按键）执行
5. 右键点击或不做操作等待超时，则直接关闭不执行任何内容
6. 额外的按键直达指令：进入 "Custom Shortcuts" 子页面，添加一行并绑定按键、填入指令（如小键盘 2 → `/is`）
7. 命令别名：进入 "Command Aliases" 子页面，每行填 **你输入的命令 → 实际执行的内容**，例如 `/home` → `/warp base`；第二框不带 `/` 时会改发聊天消息
8. 屏幕提醒：进入 "Screen Alerts" 子页面，每行填 **要检测的聊天文字 → 屏幕横幅显示的内容 → 监听频道**，命中后文字在屏幕中上方放大显示 4 秒，可用 `[player]`、`[me]` 占位符和 § 颜色码
9. 自动聊天规则：进入 "Custom Chat Rules" 子页面，每行依次填写 **匹配文本 → 动作（`/` 开头为指令）→ 频道 → Aa 大小写开关 → 包含/正则模式**，可用 `[player]`、`[me]`、`$1`–`$9` 占位符
10. 聊天音效：进入 "Chat Sounds" 子页面，每行填 **匹配文本 → 音效（左键切换、右键试听）→ ×n 连播次数 → 频道 → Aa → 包含/正则**，命中后在本地播放原版音效，适合用猫叫/叮声提醒稀有掉落或关键词
11. 矿井尸体播报默认开启：进入冰川矿井后会自动用 `/pc` 发出尸体数量与类别，可在 "Mineshaft Corpses" 子页面关闭或调整为不限服务器
12. 私信 `!invite` 自动邀请默认开启，可在 "Auto Party Invite" 子页面关闭或调整为不限服务器
13. 实体高亮：进入 "Entity Highlights" 子页面，每行填 **生物名字 → 轮廓颜色**，匹配到的生物会发光并隔墙可见；仅对生物生效（盔甲架除外）
14. 所有设置保存在 `.minecraft/config/skyziyou.json`，可手动编辑，删除该文件即可重置（旧版 `commandwheel.json` 会在首次启动时自动迁移）

## 配置指南与示例

各子页面的行式列表通用操作：行首 ✓/✕ 控制该行启用/停用，行尾红色 X 删除该行，底部按钮添加新行，行数超出时可上下滚动；所有改动即时生效，Esc 或 Done 时自动保存。

### Custom Chat Rules（自定义聊天规则）

页面顶部三个总开关：**Enabled**（总开关）、**Log**（命中后在本地聊天栏显示一条 `[ChatRules] ...` 日志，方便调试）、**Hypixel Only**（仅在 Hypixel 服务器生效，建议保持开启）。

每行从左到右的字段：

| 控件 | 含义 |
|---|---|
| ✓/✕ | 该行规则的开关 |
| Match（第一个框） | 要匹配的聊天内容 |
| Action（第二个框） | 命中后执行的动作：`/` 开头为指令，否则发送聊天消息 |
| 频道按钮（ANY/PC/GC/AC/PM/SYS） | 只在指定频道触发：ANY=任意、PC=队伍、GC=公会、AC=全局公共聊天、PM=私信、SYS=系统消息 |
| Aa | 绿色=忽略大小写，红色=区分大小写 |
| `.*`/Ab | 绿色 `.*`=正则模式，灰色 Ab=普通"包含文字"模式 |
| X | 删除该行 |

匹配的对象是**聊天正文**（已去掉 `Party > 玩家名:` 前缀和颜色码）。频道靠服务器下发的 § 颜色码识别，玩家自己在全局聊天里打出 "Party >" 文字无法冒充队伍消息。

占位符（匹配框和动作框都可用）：

- `[me]`：你自己的昵称（匹配框里用于"别人提到我"）
- `[player]`：消息发送者昵称（SYS 系统消息没有发送者，会替换为空）
- `$1`–`$9`：仅正则模式，替换为对应捕获组内容

示例：

| 频道 | 模式 | Match | Action | 效果 |
|---|---|---|---|---|
| PC | 包含 | `!warp` | `/p warp` | 队伍里有人发 `!warp`，自动执行 `/p warp` 拉人 |
| GC | 正则 | `@[me] !i` | `/p [player]` | 公会里有人发 `@你的名字 !i`，自动邀请进队（首启自带） |
| GC | 正则 | `@[me] boop` | `/boop [player]` | 有人在公会 @你 并说 boop，自动 boop 回去（首启自带） |
| PM | 正则 | `^!pay (\d+)$` | `/pay [player] $1` | 别人私信 `!pay 1000` → 自动执行 `/pay 对方 1000`（自动付款类规则请谨慎添加） |
| ANY | 包含 | `[me] afk?` | `/pc back soon` | 任意频道有人问你是不是挂机，自动在队伍频道回复 |

安全机制：自己发出的消息和回声不会触发规则；每条规则 **3 秒冷却**；所有动作进入全局队列按 **1 秒一条**发送（队列上限 32 条，断线自动清空），避免被 Hypixel 限流；正则写错时只在聊天栏红字提示一次并自动停用该行，不会崩游戏。

### Chat Sounds（聊天音效）

页面顶部两个开关：**Enabled**（总开关）、**Log**（命中后在本地聊天栏显示一条 `[ChatSounds] ...` 日志，默认开启，方便确认规则是否生效）。命中规则后只在本地播放音效，**不会向服务器发送任何内容**，因此没有"仅 Hypixel"限制；不想在单机听到提示时关掉总开关即可。

每行从左到右：

| 控件 | 含义 |
|---|---|
| ✓/✕ | 该行规则的开关 |
| Match（输入框） | 要匹配的聊天内容（支持 `[me]` 占位符） |
| 音效按钮 | 点击打开**音效选择面板**：可滚动列出全部 23 种原版音效和已导入的自定义音效，点条目即选用，点条目右侧 ▶ 试听 |
| ▶ | 按当前连播次数立即试听该行选中的音效 |
| ×n 按钮 | 点击在 **×1～×5** 之间循环，控制音效连续播放次数（每次间隔约 0.25 秒） |
| 频道按钮（ANY/PC/GC/AC/PM/SYS） | 同聊天规则 |
| Aa | 绿色=忽略大小写，红色=区分大小写 |
| `.*`/Ab | 绿色 `.*`=正则模式，灰色 Ab=普通包含模式 |
| X | 删除该行 |

**自定义音效**：在音效选择面板底部点 **＋ 添加自定义音效…**，会弹出系统文件选择窗口，选择一个 **.ogg（Ogg Vorbis）或 .mp3** 文件（单个 ≤ 10 MB、时长 ≤ 60 秒），然后在游戏内弹窗为它命名并点"导入"。导入在后台完成（不卡游戏），成功后音效立即出现在面板的"自定义音效"分区；每个自定义音效右侧有 ✎ 重命名和 X 删除。原始文件复制保存在 `.minecraft/config/skyziyou/sounds/`（MP3 保留原文件、内部按解码后的 PCM 播放，不重新编码、无音质损失），播放走独立于游戏音效引擎的 Java Sound 音频通道，不修改任何游戏资源，**F3+T 重载资源、插拔耳机/切换音频设备都不受影响、无需重启**。格式不符 / 文件损坏 / 超限时弹窗会给出明确的中文错误提示。删除自定义音效后，引用它的规则会显示"（自定义音效已删除）"，重新添加或改选其他音效即可。

示例：

| 频道 | 模式 | Match | 音效 | 效果 |
|---|---|---|---|---|
| SYS | 包含 | `RARE DROP!` | 猫叫 / 狗叫 | 出稀有掉落时一声动物叫提醒 |
| PC | 包含 | `[me]` | 经验球（叮） | 队伍里有人提到你时"叮"一声 |
| ANY | 正则 | `\b(bingo|wither)\b` | 铃铛 | 关键词出现时响铃 |

安全机制与聊天规则一致：自己发出的消息不触发；每条规则 **2 秒冷却**防止刷屏；非法正则只红字提示一次并停用该行。音量跟随游戏主音量（MASTER 声道、2D 无衰减）。

### Command Aliases（命令别名）

每行：✓/✕ → 你输入的命令（带不带 `/` 均可）→ 实际执行的内容 → X。**只精确匹配整条命令**（忽略大小写），带额外参数时不触发，原命令照常发出。仅对以 `/` 开头的输入生效；想一键发送普通聊天文字请用 Custom Shortcuts。

| 你输入 | 实际执行 | 说明 |
|---|---|---|
| `/pl` | `/p leave` | 一键退队（首启自带） |
| `/dt` | `/warp dungeon_hub` | 快捷传送地下城大厅 |
| `/hi` | `hello everyone` | 第二框不带 `/` 时作为聊天消息发出 |

### Screen Alerts（屏幕提醒）

每行：✓/✕ → 触发文字（忽略大小写的包含匹配）→ 横幅显示文字（留空则显示触发原文）→ 频道（ANY/PC/GC/AC/PM/SYS）→ X。横幅文字支持 § 颜色码和 `[player]`、`[me]` 占位符。

| 频道 | 触发文字 | 横幅内容 | 效果 |
|---|---|---|---|
| PC | `warp` | `§a[player] §f发起了 warp` | 队伍有人说 warp 时绿色提示是谁发起的 |
| SYS | `You found` | `§6§l掉落提示！` | 出现"You found …"掉落信息时金色大字提醒 |
| PM | `!invite` | `§c[player] 请求邀请` | 收到邀请请求私信时红色提示 |

横幅显示 4 秒；相同内容只刷新计时不重复堆叠，不同内容最多同时显示 5 条；页面顶部的 −/+ 调整字号（1.0–5.0 倍），下方有等比例实时预览。

### Custom Shortcuts（自定义快捷键）

每行：✓/✕ → 按键按钮（点击后按任意键/鼠标键）→ 要执行的指令或消息 → X。按下立即执行，适合任何常用操作，例如：小键盘 `2` → `/is`、`G` → `/p warp`、鼠标侧键 → `/hub`。两个快捷键绑到同一个键时页面上方会出现红色 ⚠ 冲突警告。

### Entity Highlights（实体高亮）

每行：✓/✕ → 生物名字（忽略大小写的包含匹配）→ 颜色按钮（点击循环 15 种原版颜色）→ X。同时匹配生物自带名字和头顶全息名字；多条规则同时命中同一生物时，**列表最上方的规则生效**。示例：

| 名字 | 颜色 | 用途 |
|---|---|---|
| `Blacksmith` | 绿色 | Hub 铁匠 NPC |
| `Lapis Zombie` | 红色 | 矿井里的青金僵尸 |
| `Ice Walker` | 青色 | 矿井冰行者 |

### Auto Party Invite（私信自动邀请）

仅当收到的私信**正文恰好等于** `!invite`（忽略大小写）时自动执行 `/p <玩家>`；`!invite please` 之类的内容不会触发。同一玩家 60 秒内只触发一次，指令同样走 1 秒一条的全局队列。

## 从源码构建

本仓库使用 [Stonecutter](https://github.com/kikugie-studio/stonecutter) 管理多版本，同一份源码可分别构建 26.1 与 26.2 的 jar。

**JDK 要求**：运行 Gradle 需要任意 JDK 17 或更高版本（使用 `JAVA_HOME` / PATH 中的即可）；编译与游戏运行所需的 **JDK 25** 由 Gradle 工具链自动探测，本机没有时会通过 foojay 自动下载，无需在配置文件中写死 JDK 路径。

```powershell
git clone https://github.com/orchidsziyou/Skyziyou.git
cd Skyziyou

# Windows (PowerShell) —— 注意任务名冒号两侧不能有空格
.\gradlew.bat :26.1:build :26.2:build   # 两个版本一起构建
.\gradlew.bat :26.2:build               # 只构建 26.2

# Linux / macOS
./gradlew :26.1:build :26.2:build
```

构建产物（使用不带 `-sources` 后缀的 jar）：

- 26.1：`versions/26.1/build/libs/Skyziyou-Fabric+mc26.1-<版本号>.jar`
- 26.2：`versions/26.2/build/libs/Skyziyou-Fabric+mc26.2-<版本号>.jar`

直接运行 `./gradlew build`（不带版本前缀）只构建当前激活的版本。

## 项目结构

```
src/main/
├── java/com/skyziyou/mixin/
│   ├── MixinMouseHandler.java                  # Mixin：锁定视角、吞掉点击、采集鼠标位移
│   ├── EntityAccessor.java                     # Mixin Invoker：setSharedFlag(6) 点亮实体发光
│   └── ClientCommonPacketListenerAccessor.java # 读取服务器品牌（Hypixel 检测）
├── kotlin/com/skyziyou/
│   ├── SkyziyouMod.kt          # 模组入口：初始化配置/渲染器/事件注册
│   ├── ConfigScreen.kt         # 分类注册表 + BaseConfigScreen 侧栏外壳基类
│   ├── SkyWidgets.kt           # 深色主题控件：SkyButton / SkyToggle（✓/✕）
│   ├── GeneralScreen.kt        # General 综述页：版本号 / GitHub 链接 / 检查更新入口
│   ├── UpdateChecker.kt        # 只读查询 GitHub 最新 release（后台线程，不自动下载）
│   ├── WheelState.kt           # 轮盘核心状态机：打开/选中/关闭/执行
│   ├── WheelRenderer.kt        # HUD 渲染：扫描线圆盘 + 文字标签
│   ├── SkyziyouConfig.kt       # JSON 配置读写（原子写入、旧配置自动迁移）
│   ├── WheelConfigScreen.kt    # Command Wheel 子页面
│   ├── Shortcuts.kt / ShortcutsScreen.kt       # 自定义按键快捷键
│   └── features/
│       ├── ActionQueue.kt      # 全局命令/聊天限速队列（1 秒一条，断线清空）
│       ├── chat/               # 统一聊天频道识别 + 自定义聊天规则
│       │   ├── ChatChannel.kt / ChatClassifier.kt
│       │   └── ChatRulesFeature.kt / ChatRulesScreen.kt
│       ├── sound/              # 聊天内容匹配后播放原版内置音效（精选音效表）
│       │   ├── SoundLibrary.kt / ChatSounds.kt
│       │   └── ChatSoundsScreen.kt
│       ├── command/            # 自定义命令别名（手输命令改写）
│       │   └── CommandAliases.kt / CommandAliasesScreen.kt
│       ├── alert/              # 聊天关键词触发的屏幕中上方横幅提醒
│       │   └── ScreenAlerts.kt / ScreenAlertsScreen.kt
│       ├── mineshaft/          # 进入矿井后读取 Tab 尸体信息并 /pc 播报
│       │   └── MineshaftCorpseReporter.kt / MineshaftCorpseScreen.kt
│       ├── party/
│       │   ├── AutoInviteFeature.kt        # 私信 !invite 自动邀请
│       │   └── AutoInviteConfigScreen.kt   # 该功能的设置子页面
│       └── highlight/                      # 实体发光高亮（名字+全息文字融合匹配、假队伍着色）
│           └── EntityHighlights.kt / EntityHighlightsScreen.kt
└── resources/
    └── skyziyou.mixins.json    # Mixin 配置（fabric.mod.json 按版本放在 versions/ 下）

versions/26.1/src/main/resources/fabric.mod.json   # 26.1 模组元数据（依赖 ~26.1）
versions/26.2/src/main/resources/fabric.mod.json   # 26.2 模组元数据（依赖 ~26.2）
settings.gradle.kts / stonecutter.gradle.kts       # Stonecutter 多版本构建配置
```

## 许可证

[MIT](LICENSE)

自定义音效的 MP3 解码使用 [JLayer](http://www.javazoom.net/javalayer/javalayer.html)（JavaZOOM，LGPL-2.1），以**未修改的原始 jar** 通过 Jar-in-Jar 方式随附，你可以自行替换该库；OGG 解码使用 Minecraft 自带的 JOrbis。
