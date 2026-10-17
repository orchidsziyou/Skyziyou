package com.skyziyou

/**
 * Tiny in-mod localisation for the settings UI. The active language ([language],
 * one of [EN]/[ZH]) is persisted in [SkyziyouConfig] and applied on config load;
 * every settings screen reads strings through [t] while building/rendering, so
 * flipping the language on the main menu takes effect immediately and on every
 * subsequently opened sub-page.
 *
 * Only the config interface is translated: messages actually sent to the server
 * (party reports, replies, etc.) stay English on purpose.
 */
object I18n {

    const val EN = "EN"
    const val ZH = "ZH"

    var language: String = EN

    /** Returns the string for [key] in the active language; `{0}`.. placeholders are filled from [args]. */
    fun t(key: String, vararg args: Any?): String {
        var text = table()[key] ?: en[key] ?: key
        args.forEachIndexed { index, arg -> text = text.replace("{$index}", arg.toString()) }
        return text
    }

    fun isZh(): Boolean = language == ZH

    private fun table(): Map<String, String> = if (language == ZH) zh else en

    private val en = mapOf(
        // common
        "common.done" to "Done",
        "common.back" to "Back",
        "common.on" to "ON",
        "common.off" to "OFF",
        "common.enabled" to "Enabled",
        "common.log" to "Log",
        "common.hypixel_only" to "Hypixel Only",
        "common.not_set" to "not set",
        "common.language_button" to "Language: English",

        // main menu
        "main.title" to "Skyziyou Settings",
        "cat.general" to "General",
        "cat.general.desc" to "Mod information and links.",
        "cat.wheel" to "Command Wheel",
        "cat.wheel.desc" to "Wheel slots, trigger key and behaviour.",
        "cat.shortcuts" to "Custom Shortcuts",
        "cat.shortcuts.desc" to "Bind keys directly to commands or chat.",
        "cat.chatrules" to "Custom Chat Rules",
        "cat.chatrules.desc" to "Match chat text, auto-reply or run commands.",
        "cat.sounds" to "Chat Sounds",
        "cat.sounds.desc" to "Play a built-in game sound when chat text appears.",
        "cat.aliases" to "Command Aliases",
        "cat.aliases.desc" to "Rewrite a typed command into another one.",
        "cat.alerts" to "Screen Alerts",
        "cat.alerts.desc" to "Show a banner on screen when chat text appears.",
        "cat.corpses" to "Mineshaft Corpses",
        "cat.corpses.desc" to "Post corpse counts to party chat in mineshafts.",
        "cat.invite" to "Auto Party Invite",
        "cat.invite.desc" to "Auto-invite players that PM you !invite.",
        "cat.highlights" to "Entity Highlights",
        "cat.highlights.desc" to "Glow living mobs by their name.",

        // general page
        "general.version" to "§7Version §f{0}",
        "general.line1" to "§7Hypixel SkyBlock utility mod.",
        "general.line2" to "§7Wheel, shortcuts, chat rules, highlights and more.",
        "general.open_github" to "Open GitHub Page",
        "general.check_updates" to "Check for Updates",
        "general.open_releases" to "Open Releases Page",
        "general.update_idle" to "§7Click the button to check for a new version.",
        "general.update_checking" to "§7Checking GitHub for updates…",
        "general.update_latest" to "§aYou are up to date (v{0}).",
        "general.update_available" to "New version v{0} available! Open the releases page to download.",
        "general.update_failed" to "Update check failed: {0}",

        // command wheel page
        "wheel.keybind_listening" to "Wheel Keybind: press any key...",
        "wheel.keybind" to "Wheel Keybind: {0}",
        "wheel.execute_on_release" to "Execute On Release",
        "wheel.execution_log" to "Execution Log",
        "wheel.dead_zone" to "Dead Zone",
        "wheel.size_pct" to "Wheel Size %",
        "wheel.font_pct" to "Font Size %",
        "wheel.timeout_s" to "Timeout s",

        // shortcuts page
        "shortcuts.listening" to "Key: press any key...",
        "shortcuts.key" to "Key: {0}",
        "shortcuts.hint" to "/command or chat message",
        "shortcuts.add" to "Add Shortcut",
        "shortcuts.conflict" to "§c⚠ duplicate keys will all trigger",

        // chat rules page
        "chatrules.match_hint" to "match text",
        "chatrules.action_hint" to "/command or reply",
        "chatrules.header_match" to "§7match ([me] = you)",
        "chatrules.header_action" to "§7action  (use [player], $1..$9)",
        "chatrules.legend" to "§7Ch: ANY/PC/GC/AC/PM/SYS · Aa: green=ignore case, red=exact · Ab=contains  .*=regex",
        "chatrules.add" to "Add Rule",

        // chat sounds page
        "sounds.match_hint" to "match text",
        "sounds.header_match" to "§7match ([me] = you)",
        "sounds.header_sound" to "§7sound (click to pick)",
        "sounds.header_repeat" to "§7×n",
        "sounds.legend" to "§7Ch: ANY/PC/GC/AC/PM/SYS · ×n: play 1-5 times · Aa/regex as on chat rules · ▶ previews the sound",
        "sounds.add" to "Add Sound Rule",
        "sounds.death_row" to "On death",
        "sounds.custom_missing" to "(deleted custom sound)",
        "sounds.picker_title" to "Select a sound",
        "sounds.picker_builtin" to "BUILT-IN SOUNDS",
        "sounds.picker_custom" to "CUSTOM SOUNDS",
        "sounds.picker_empty" to "No custom sounds yet — add one below.",
        "sounds.picker_format" to "Only MP3 or Ogg Vorbis audio (.mp3 / .ogg) is supported.",
        "sounds.add_custom" to "＋ Add custom sound…",
        "sounds.dialog_title" to "Choose an audio file (.ogg / .mp3)",
        "sounds.dialog_filter" to "Audio files (*.ogg, *.mp3)",
        "sounds.import_title" to "Name your sound",
        "sounds.rename_title" to "Rename sound",
        "sounds.name_hint" to "sound name",
        "sounds.format_hint" to "Ogg Vorbis or MP3 · max 10 MB / 60 s · kept locally",
        "sounds.import_confirm" to "Import",
        "sounds.importing" to "Importing…",
        "sounds.rename_confirm" to "Rename",
        "sounds.err_name" to "Please enter a name.",
        "sounds.err_format" to "Unsupported format: only .ogg (Ogg Vorbis) and .mp3 are accepted.",
        "sounds.err_decode" to "Could not decode this file — is it a valid Ogg Vorbis / MP3?",
        "sounds.err_size" to "File too large: the limit is 10 MB.",
        "sounds.err_length" to "Audio too long: the limit is 60 seconds.",
        "sounds.err_read" to "Could not read the chosen file.",
        "sounds.choice.experience_orb" to "XP Orb (ding)",
        "sounds.choice.pling" to "Pling",
        "sounds.choice.bell" to "Bell",
        "sounds.choice.chime" to "Chimes",
        "sounds.choice.harp" to "Harp",
        "sounds.choice.bit" to "8-bit",
        "sounds.choice.cow_bell" to "Cow Bell",
        "sounds.choice.levelup" to "Level Up",
        "sounds.choice.toast" to "Toast Pop",
        "sounds.choice.click" to "Button Click",
        "sounds.choice.cat" to "Cat Meow",
        "sounds.choice.wolf" to "Wolf Bark",
        "sounds.choice.chicken" to "Chicken",
        "sounds.choice.pig" to "Pig",
        "sounds.choice.cow" to "Cow",
        "sounds.choice.sheep" to "Sheep",
        "sounds.choice.fox" to "Fox",
        "sounds.choice.frog" to "Frog",
        "sounds.choice.villager_yes" to "Villager: Yes",
        "sounds.choice.villager_no" to "Villager: No",
        "sounds.choice.arrow" to "Arrow Hit",
        "sounds.choice.anvil" to "Anvil Land",
        "sounds.choice.dragon" to "Dragon Growl",

        // command aliases page
        "aliases.title" to "Custom Command Aliases",
        "aliases.typed_hint" to "type this, e.g. /home",
        "aliases.target_hint" to "runs this: /cmd or chat",
        "aliases.header_typed" to "§7typed command (exact match)",
        "aliases.header_target" to "§7runs instead",
        "aliases.footer" to "§7Exact match only: extra arguments are not forwarded · target: '/' = command, otherwise chat",
        "aliases.add" to "Add Alias",

        // screen alerts page
        "alerts.trigger_hint" to "chat text to watch",
        "alerts.text_hint" to "banner text ([player])",
        "alerts.header_trigger" to "§7trigger (contains)",
        "alerts.header_banner" to "§7banner shown for 4s",
        "alerts.header_ch" to "§7ch",
        "alerts.font_size" to "§7Font size: §f{0}x",
        "alerts.preview_caption" to "§7Preview (actual in-game size)",
        "alerts.preview" to "Alert Preview",
        "alerts.legend" to "§7Ch: ANY/PC/GC/AC/PM/SYS · case-insensitive · use [player]/[me] and § codes",
        "alerts.add" to "Add Alert",

        // mineshaft corpse page
        "corpses.title" to "Mineshaft Corpse Report",
        "corpses.detailed" to "Detailed Format",
        "corpses.line1" to "§7Reads the §fFrozen Corpses §7tab widget right after entering a mineshaft",
        "corpses.line2" to "§7and sends the corpse counts to party chat once via §f/pc§7.",
        "corpses.line4" to "§7Detailed: §f/pc Frozen Corpses (4): 2x Lapis, 1x Umber, 1x Vanguard",
        "corpses.line5" to "§7Brief:    §f/pc Corpses (4): 2l 1u 1vg",
        "corpses.line6" to "§7Tokens: §9l §7=Lapis  §8t §7=Tungsten  §6u §7=Umber  §fvg §7=Vanguard",

        // auto party invite page
        "invite.invite_log" to "Invite Log",
        "invite.line1" to "§7Invites a player when their private message is exactly §f!invite§7.",
        "invite.line2" to "§7Runs §f/p <player>§7; the message is matched case-insensitively.",
        "invite.line3" to "§7Same player cooldown: §f60s§7 · commands are spaced §f1s§7 apart (queued).",

        // entity highlights page
        "highlights.name_hint" to "entity name",
        "highlights.header_name" to "§7entity name (contains, ignore case)",
        "highlights.header_color" to "§7colour",
        "highlights.legend" to "§7Mobs & NPCs · hologram name text above them is recognised too · glow shows through walls",
        "highlights.add" to "Add Highlight",
    )

    private val zh = mapOf(
        // common
        "common.done" to "完成",
        "common.back" to "返回",
        "common.on" to "开",
        "common.off" to "关",
        "common.enabled" to "启用",
        "common.log" to "日志",
        "common.hypixel_only" to "仅 Hypixel",
        "common.not_set" to "未设置",
        "common.language_button" to "语言：中文",

        // main menu
        "main.title" to "Skyziyou 设置",
        "cat.general" to "综述",
        "cat.general.desc" to "模组信息与相关链接。",
        "cat.wheel" to "指令轮盘",
        "cat.wheel.desc" to "轮盘槽位、触发按键与行为设置。",
        "cat.shortcuts" to "自定义快捷键",
        "cat.shortcuts.desc" to "把按键直接绑定到指令或聊天消息。",
        "cat.chatrules" to "自定义聊天规则",
        "cat.chatrules.desc" to "匹配聊天文字，自动回复或执行指令。",
        "cat.sounds" to "聊天音效",
        "cat.sounds.desc" to "聊天出现指定文字时播放游戏内置音效。",
        "cat.aliases" to "指令别名",
        "cat.aliases.desc" to "把你输入的指令改写成另一条。",
        "cat.alerts" to "屏幕提醒",
        "cat.alerts.desc" to "聊天出现指定文字时在屏幕上显示横幅。",
        "cat.corpses" to "矿井尸体播报",
        "cat.corpses.desc" to "在矿井中把尸体数量发到队伍频道。",
        "cat.invite" to "自动组队邀请",
        "cat.invite.desc" to "私信你 !invite 时自动邀请入队。",
        "cat.highlights" to "实体高亮",
        "cat.highlights.desc" to "按生物名字给其添加发光轮廓。",

        // general page
        "general.version" to "§7版本 §f{0}",
        "general.line1" to "§7Hypixel SkyBlock 功能辅助模组。",
        "general.line2" to "§7轮盘、快捷键、聊天规则、实体高亮等功能。",
        "general.open_github" to "打开 GitHub 页面",
        "general.check_updates" to "检查更新",
        "general.open_releases" to "打开 Releases 下载页",
        "general.update_idle" to "§7点击按钮检查是否有新版本。",
        "general.update_checking" to "§7正在向 GitHub 查询更新…",
        "general.update_latest" to "§a当前已是最新版本（v{0}）。",
        "general.update_available" to "发现新版本 v{0}！打开 Releases 页面下载。",
        "general.update_failed" to "检查更新失败：{0}",

        // command wheel page
        "wheel.keybind_listening" to "轮盘按键：请按任意键…",
        "wheel.keybind" to "轮盘按键：{0}",
        "wheel.execute_on_release" to "松开按键时执行",
        "wheel.execution_log" to "执行日志",
        "wheel.dead_zone" to "死区",
        "wheel.size_pct" to "轮盘大小 %",
        "wheel.font_pct" to "字体大小 %",
        "wheel.timeout_s" to "超时（秒）",

        // shortcuts page
        "shortcuts.listening" to "按键：请按任意键…",
        "shortcuts.key" to "按键：{0}",
        "shortcuts.hint" to "/指令或聊天内容",
        "shortcuts.add" to "添加快捷键",
        "shortcuts.conflict" to "§c⚠ 存在重复按键，按下时都会触发",

        // chat rules page
        "chatrules.match_hint" to "匹配文本",
        "chatrules.action_hint" to "/指令或回复",
        "chatrules.header_match" to "§7匹配（[me] = 自己）",
        "chatrules.header_action" to "§7动作（可用 [player]、$1..$9）",
        "chatrules.legend" to "§7频道：ANY/PC/GC/AC/PM/SYS · Aa：绿=忽略大小写，红=精确 · Ab=包含  .*=正则",
        "chatrules.add" to "添加规则",

        // chat sounds page
        "sounds.match_hint" to "匹配文本",
        "sounds.header_match" to "§7匹配（[me] = 自己）",
        "sounds.header_sound" to "§7音效（点击选择）",
        "sounds.header_repeat" to "§7次数",
        "sounds.legend" to "§7频道：ANY/PC/GC/AC/PM/SYS · ×n：连播 1-5 次 · Aa/正则同聊天规则 · ▶ 试听当前音效",
        "sounds.add" to "添加音效规则",
        "sounds.death_row" to "角色死亡时",
        "sounds.custom_missing" to "（自定义音效已删除）",
        "sounds.picker_title" to "选择音效",
        "sounds.picker_builtin" to "原版音效",
        "sounds.picker_custom" to "自定义音效",
        "sounds.picker_empty" to "还没有自定义音效，点击下方按钮添加。",
        "sounds.picker_format" to "仅支持 MP3 或 Ogg Vorbis 格式的音频（.mp3 / .ogg）。",
        "sounds.add_custom" to "＋ 添加自定义音效…",
        "sounds.dialog_title" to "选择音频文件（.ogg / .mp3）",
        "sounds.dialog_filter" to "音频文件（*.ogg、*.mp3）",
        "sounds.import_title" to "为音效命名",
        "sounds.rename_title" to "重命名音效",
        "sounds.name_hint" to "音效名称",
        "sounds.format_hint" to "支持 Ogg Vorbis / MP3 · 最大 10 MB / 60 秒 · 仅保存在本地",
        "sounds.import_confirm" to "导入",
        "sounds.importing" to "导入中…",
        "sounds.rename_confirm" to "重命名",
        "sounds.err_name" to "请填写名称。",
        "sounds.err_format" to "不支持的格式：仅支持 .ogg（Ogg Vorbis）和 .mp3。",
        "sounds.err_decode" to "无法解码该文件，请确认它是有效的 Ogg Vorbis / MP3 文件。",
        "sounds.err_size" to "文件过大：上限为 10 MB。",
        "sounds.err_length" to "音频过长：上限为 60 秒。",
        "sounds.err_read" to "无法读取所选文件。",
        "sounds.choice.experience_orb" to "经验球（叮）",
        "sounds.choice.pling" to "清脆叮声",
        "sounds.choice.bell" to "铃铛",
        "sounds.choice.chime" to "风铃",
        "sounds.choice.harp" to "竖琴",
        "sounds.choice.bit" to "8-bit 电子音",
        "sounds.choice.cow_bell" to "牛铃",
        "sounds.choice.levelup" to "升级音效",
        "sounds.choice.toast" to "提示弹窗",
        "sounds.choice.click" to "按钮点击",
        "sounds.choice.cat" to "猫叫",
        "sounds.choice.wolf" to "狗叫",
        "sounds.choice.chicken" to "鸡叫",
        "sounds.choice.pig" to "猪叫",
        "sounds.choice.cow" to "牛叫",
        "sounds.choice.sheep" to "羊叫",
        "sounds.choice.fox" to "狐狸叫",
        "sounds.choice.frog" to "青蛙叫",
        "sounds.choice.villager_yes" to "村民：好",
        "sounds.choice.villager_no" to "村民：不",
        "sounds.choice.arrow" to "中箭",
        "sounds.choice.anvil" to "铁砧落地",
        "sounds.choice.dragon" to "末影龙低吼",

        // command aliases page
        "aliases.title" to "自定义指令别名",
        "aliases.typed_hint" to "输入的内容，如 /home",
        "aliases.target_hint" to "实际执行：/指令 或聊天",
        "aliases.header_typed" to "§7输入的指令（精确匹配）",
        "aliases.header_target" to "§7实际执行的内容",
        "aliases.footer" to "§7仅精确匹配：不转发额外参数 · 目标以 / 开头为指令，否则为聊天",
        "aliases.add" to "添加别名",

        // screen alerts page
        "alerts.trigger_hint" to "要检测的聊天文字",
        "alerts.text_hint" to "横幅内容（[player]）",
        "alerts.header_trigger" to "§7检测词（包含匹配）",
        "alerts.header_banner" to "§7横幅显示 4 秒",
        "alerts.header_ch" to "§7频道",
        "alerts.font_size" to "§7字号：§f{0}x",
        "alerts.preview_caption" to "§7预览（游戏内实际大小）",
        "alerts.preview" to "提醒预览",
        "alerts.legend" to "§7频道：ANY/PC/GC/AC/PM/SYS · 忽略大小写 · 可用 [player]/[me] 和 § 颜色码",
        "alerts.add" to "添加提醒",

        // mineshaft corpse page
        "corpses.title" to "矿井尸体播报",
        "corpses.detailed" to "详细格式",
        "corpses.line1" to "§7进入矿井后读取 Tab 列表中的 §fFrozen Corpses §7区块",
        "corpses.line2" to "§7并通过 §f/pc§7 向队伍频道发送一次各类尸体数量。",
        "corpses.line4" to "§7详细：§f/pc Frozen Corpses (4): 2x Lapis, 1x Umber, 1x Vanguard",
        "corpses.line5" to "§7简略：§f/pc Corpses (4): 2l 1u 1vg",
        "corpses.line6" to "§7代号：§9l §7=Lapis  §8t §7=Tungsten  §6u §7=Umber  §fvg §7=Vanguard",

        // auto party invite page
        "invite.invite_log" to "邀请日志",
        "invite.line1" to "§7当玩家私信内容恰好为 §f!invite§7 时自动邀请该玩家。",
        "invite.line2" to "§7执行 §f/p <玩家>§7，消息匹配忽略大小写。",
        "invite.line3" to "§7同一玩家冷却 §f60 秒§7 · 指令间隔 §f1 秒§7 排队发送。",

        // entity highlights page
        "highlights.name_hint" to "生物名字",
        "highlights.header_name" to "§7生物名字（包含匹配，忽略大小写）",
        "highlights.header_color" to "§7颜色",
        "highlights.legend" to "§7生物与 NPC · 头顶全息名字也可匹配 · 发光轮廓隔墙可见 · 原版 15 种队伍颜色",
        "highlights.add" to "添加高亮",
    )
}
