# Skyziyou

[English](./README_EN.md) | 简体中文

> 本模组为 AI 辅助生成，如有介意请勿使用。

本模组为 Minecraft Fabric 端多功能辅助模组（主要用于 Hypixel SkyBlock）：按住一个自定义按键即可在屏幕中央打开一个 8 方位的指令轮盘，鼠标向某个方向滑动选中扇区，然后左键点击（或松开按键）即可快速执行预设指令或发送聊天消息；此外还包含自定义快捷键、按聊天频道自动触发动作的自定义聊天规则、私信自动邀请等独立功能。支持 **Minecraft 26.1 与 26.2**，两个版本由同一份源码构建、分别发布对应 jar。

## 界面预览

**游戏内轮盘：**

![游戏内轮盘](UI.png)

**设置界面：**

![设置界面](setting.png)

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
- **内置分类设置界面**：游戏内输入 `/skyziyou` 或 `/sz` 打开，各功能位于独立子页面，所有改动即时生效、关闭时自动保存
- **自定义快捷键**：任意按键（含鼠标键，如小键盘 2）可直接绑定一条指令或消息，按下立即执行，在 "Custom Shortcuts" 子页面配置；每条可单独启用/停用，按键冲突会标红提示
- **Custom Chat Rules（自定义聊天规则）**：按聊天频道（任意 / 队伍 / 公会 / 全局 / 私信 / 系统）匹配收到的聊天行，自动发送回复或执行指令，在 "Custom Chat Rules" 子页面配置
  - 匹配方式：忽略大小写的包含匹配，或完整正则匹配（非法正则只提示一次并自动停用）
  - 动作：以 `/` 开头作为指令执行，否则作为聊天消息发送
  - 占位符：`[player]` = 发送者昵称、`[me]` = 自己的昵称；正则模式额外支持 `$1`–`$9` 捕获组替换
  - 频道前缀按服务器下发的 § 颜色码识别，玩家无法通过打字伪造队伍 / 公会消息触发规则
  - 每条规则 3 秒冷却，所有动作经全局队列按 1 秒一条限速发送（断线自动清空），可限定仅 Hypixel 生效
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

1. 游戏内输入 `/skyziyou`（简写 `/sz`）打开设置分类界面
2. 进入 **Command Wheel** 子页面，点击 **Wheel Keybind** 按钮后按下任意键（或鼠标键）绑定触发键
3. 在右侧 8 个输入框中填入各方位要执行的指令或消息（留空表示该方位不可用）
4. 按住触发键打开轮盘 → 鼠标滑向目标方位 → 左键点击（或松开按键）执行
5. 右键点击或不做操作等待超时，则直接关闭不执行任何内容
6. 额外的按键直达指令：进入 "Custom Shortcuts" 子页面，添加一行并绑定按键、填入指令（如小键盘 2 → `/is`）
7. 自动聊天规则：进入 "Custom Chat Rules" 子页面，每行依次填写 **匹配文本 → 动作（`/` 开头为指令）→ 频道 → 包含/正则模式**，可用 `[player]`、`[me]`、`$1`–`$9` 占位符
8. 私信 `!invite` 自动邀请默认开启，可在 "Auto Party Invite" 子页面关闭或调整为不限服务器
9. 所有设置保存在 `.minecraft/config/skyziyou.json`，可手动编辑，删除该文件即可重置（旧版 `commandwheel.json` 会在首次启动时自动迁移）

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
│   └── ClientCommonPacketListenerAccessor.java # 读取服务器品牌（Hypixel 检测）
├── kotlin/com/skyziyou/
│   ├── SkyziyouMod.kt          # 模组入口：初始化配置/渲染器/事件注册
│   ├── ConfigScreen.kt         # 设置分类主页 + BaseConfigScreen 基类
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
│       └── party/
│           ├── AutoInviteFeature.kt        # 私信 !invite 自动邀请
│           └── AutoInviteConfigScreen.kt   # 该功能的设置子页面
└── resources/
    └── skyziyou.mixins.json    # Mixin 配置（fabric.mod.json 按版本放在 versions/ 下）

versions/26.1/src/main/resources/fabric.mod.json   # 26.1 模组元数据（依赖 ~26.1）
versions/26.2/src/main/resources/fabric.mod.json   # 26.2 模组元数据（依赖 ~26.2）
settings.gradle.kts / stonecutter.gradle.kts       # Stonecutter 多版本构建配置
```

## 许可证

[MIT](LICENSE)
