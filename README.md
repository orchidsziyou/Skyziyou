# Command Wheel（指令轮盘）

> 本模组为 AI 辅助生成，如有介意请勿使用。

本模组为Minecraft Fabric端模组：按住一个自定义按键即可在屏幕中央打开一个 8 方位的轮盘，鼠标向某个方向滑动选中扇区，然后左键点击（或松开按键）即可快速执行预设指令或发送聊天消息。

本模组适用于需要频繁输入指令的场景（如 Hypixel SkyBlock 里的 `/ah`、`/bz`、`/hub` 等）。

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
- **内置设置界面**：游戏内输入 `/commandwheel` 或 `/cwh` 打开，所有改动即时生效、关闭时自动保存

## 环境要求

| 项目 | 版本 |
|---|---|
| Minecraft | 26.1.x |
| Fabric Loader | >= 0.19.0 |
| Fabric API | 最新版 |
| Fabric Language Kotlin | >= 1.13.0 |
| Java | >= 25 |

仅客户端模组，服务端无需安装。

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/installer/)
2. 下载 [Fabric API](https://modrinth.com/mod/fabric-api) 与 [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)，放入 `mods` 文件夹
3. 从 [Releases](../../releases) 页面下载本模组 jar，放入 `mods` 文件夹
4. 启动游戏

## 使用方法

1. 游戏内输入 `/commandwheel`（简写 `/cwh`）打开设置界面
2. 点击 **Wheel Keybind** 按钮后按下任意键（或鼠标键）绑定触发键
3. 在右侧 8 个输入框中填入各方位要执行的指令或消息（留空表示该方位不可用）
4. 按住触发键打开轮盘 → 鼠标滑向目标方位 → 左键点击（或松开按键）执行
5. 右键点击或不做操作等待超时，则直接关闭不执行任何内容
6. 所有设置保存在 `.minecraft/config/commandwheel.json`，可手动编辑，删除该文件即可重置

## 从源码构建

```powershell
git clone https://github.com/orchidsziyou/CommandWheel.git
cd CommandWheel

# Windows (PowerShell)
.\gradlew.bat build

# Linux / macOS
./gradlew build
```

构建产物位于 `build/libs/` 目录，使用其中不带 `-sources` 后缀的 jar。

## 项目结构

```
src/main/
├── java/dev/orchidsziyou/commandwheel/mixin/
│   └── MixinMouseHandler.java   # Mixin：锁定视角、吞掉点击、采集鼠标位移
├── kotlin/dev/orchidsziyou/commandwheel/
│   ├── CommandWheelMod.kt       # 模组入口：初始化配置/渲染器/每 tick 状态机
│   ├── WheelState.kt            # 核心状态机：打开/选中/关闭/执行
│   ├── WheelRenderer.kt         # HUD 渲染：扫描线圆盘 + 文字标签
│   ├── WheelConfig.kt           # JSON 配置读写（原子写入）
│   └── WheelConfigScreen.kt     # 游戏内设置界面
└── resources/
    ├── fabric.mod.json          # 模组元数据
    └── commandwheel.mixins.json # Mixin 配置
```

## 许可证

[MIT](LICENSE)
