# AdminPro - Fabric 管理 Mod（1.21）

## 环境要求

- Minecraft 服务器: **1.21**
- Mod 加载器: **Fabric Loader 0.16.10+**
- 前置依赖: **Fabric API 0.102.0+1.21**

## 安装

1. 将 `AdminPro-1.0.0.jar` 放入服务器的 `mods/` 目录
2. 将 `fabric-api-0.102.0+1.21.jar` 也放入 `mods/` 目录
3. 启动服务器，控制台出现 `[AdminPro] 初始化完成！` 即表示加载成功

首次启动后会自动生成配置文件 `config/adminpro/config.json`。

---

## 命令

所有命令需拥有管理员权限（OP 等级 ≥ 2）。

### 主面板

| 命令 | 说明 |
|------|------|
| `/admin` | 打开管理员 GUI 图形面板 |

### 封禁管理

| 命令 | 说明 |
|------|------|
| `/admin ban <玩家> [时长秒] [原因...]` | 封禁玩家（时长 ≤ 0 或省略 = 永久） |
| `/admin unban <玩家>` | 解封玩家 |
| `/admin banlist` | 查看封禁列表 |

### 禁言管理

| 命令 | 说明 |
|------|------|
| `/admin mute <玩家> [时长秒] [原因...]` | 禁言玩家 |
| `/admin unmute <玩家>` | 解除禁言 |
| `/admin mutelist` | 查看禁言列表 |

### 奖励管理

| 命令 | 说明 |
|------|------|
| `/admin reward create <ID>` | 创建新奖励（手中物品为奖励内容） |
| `/admin reward give <玩家> <奖励ID>` | 发放奖励给指定玩家 |
| `/admin reward list` | 查看所有奖励 |
| `/admin reward delete <奖励ID>` | 删除奖励 |

---

## 配置文件

`config/adminpro/config.json`

```json
{
  "notificationPrefix": "§6[管理]§r ",
  "defaultBanReasons": ["恶意破坏", "语言攻击", "作弊", "其他"],
  "defaultBanDurations": [3600, 21600, 43200, 86400, 259200, 604800],
  "defaultMuteDurations": [600, 3600, 7200, 86400],
  "rewardLibraryFile": "reward_library.json",
  "bansFile": "bans.json",
  "mutesFile": "mutes.json"
}
```

| 字段 | 说明 |
|------|------|
| `notificationPrefix` | 消息前缀，支持 § 颜色代码 |
| `defaultBanReasons` | 封禁 GUI 中的可选原因列表 |
| `defaultBanDurations` | 封禁时长选项（秒） |
| `defaultMuteDurations` | 禁言时长选项（秒） |
| `rewardLibraryFile` | 奖励库文件名（相对数据目录） |
| `bansFile` | 封禁记录文件名 |
| `mutesFile` | 禁言记录文件名 |

---

## 数据文件

数据存储在 `config/adminpro/` 目录下：

- `config.json` — 配置文件
- `bans.json` — 封禁记录
- `mutes.json` — 禁言记录
- `reward_library.json` — 奖励库

---

## 构建

在项目根目录运行：

```bash
./gradlew build
```

生成的 jar 文件位于 `build/libs/AdminPro-1.0.0.jar`。
