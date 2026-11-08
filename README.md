# 斐讯 R1 音箱改造资料全书（2026 版）

> 目标：把全网能找到的 R1 玩法、工具、固件路线、坑与回滚方式整理到一处，并标注每一条的出处。
> 说明：标「实测」的条目在本机（RK322x / Android 5.1.1 / 固件 3448 / 无 root）复现过；其余均注明来源编号，文末有完整出处与致谢。
> 更新：2026-11-07

---

## 目录

- [0. 一分钟结论](#0-一分钟结论)
- [1. 设备与固件（实测）](#1-设备与固件实测)
- [2. 官方服务时间线](#2-官方服务时间线)
- [3. 按键与「模式」](#3-按键与模式)
- [4. 六条改造路线对比](#4-六条改造路线对比)
- [5. 社区方案总表](#5-社区方案总表)
- [6. 免拆免 root 实操（本机验证）](#6-免拆免-root-实操本机验证)
- [7. 关键机制详解（本机实测）](#7-关键机制详解本机实测)
- [8. 配网方式大全](#8-配网方式大全)
- [9. DLNA / AirPlay / 无线音频](#9-dlna--airplay--无线音频)
- [10. Root、刷机、小爱、Armbian、救砖](#10-root刷机小爱armbian救砖)
- [11. 硬件改造](#11-硬件改造)
- [12. FAQ](#12-faq)
- [13. 回滚清单](#13-回滚清单)
- [14. 覆盖范围与免责声明](#14-覆盖范围与免责声明)
- [15. 出处清单](#15-出处清单)
- [16. 致谢](#16-致谢)
- [17. 本仓库内容](#17-本仓库内容)

---

## 0. 一分钟结论

| 你的目标 | 推荐路线 | 代价 |
|---|---|---|
| 当普通蓝牙音箱（开机即用、无开机音） | **免拆免 root：ADB + R1Buddy**（本仓库） | 进入蓝牙模式后系统会关 Wi-Fi |
| 联网用语音/绑定 App | 免拆复活包 / 工具箱 / 音乐 App | 官方服务器已停，依赖第三方包 |
| DLNA / AirPlay / 氛围灯 | 免拆复活包，或拆机 root | DLNA 需要「普通模式 + 蓝牙开启」 |
| 完全自控的 Linux 音箱 | Armbian 移植（2026-09） | 必须拆机焊 USB，有变砖风险 |
| 只当有线音箱 | 丢原主板，保留外壳/电源/喇叭改 AUX | 不可逆，失去语音/蓝牙/氛围灯 |

**最关键的一条：R1 有「模式」之分。**
`sysMode=0`（普通模式）下 EchoService 会**主动踢掉**手机发起的 A2DP 连接；`sysMode=1`（蓝牙模式）才能稳定连接，但系统会关闭 Wi-Fi。两者目前无法兼得（第 7 节，实测）。

---

## 1. 设备与固件（实测）

| 项目 | 值 |
|---|---|
| SoC | Rockchip RK322x（`ro.product.device=rk322x-box`） |
| 内存 | 512MB |
| 系统 | Android 5.1.1（LMY49F） |
| 固件 | 3448（官方最后一版）[1][2] |
| SELinux | permissive |
| `ro.debuggable` | 1（ADB 可用，但 `shell` 是 uid 2000） |
| root | 无 `su`（需拆机，见第 10 节） |
| ADB | TCP 5555；`pm` 需用绝对路径 `/system/bin/pm` |
| 蓝牙 | A2DP **Sink**（音箱端），名称 `Phicomm_R1_xxxx` |
| 麦克风 | 4 麦阵列 [20] |
| LED | 底部氛围灯环，`/sys/class/leds/multi_leds0/led_color`，需 root [24] |
| 按键 | 顶部单个多功能键 + 触摸环 [20] |

系统关键包（**不要乱冻**）：

| 包名 | 作用 |
|---|---|
| `com.phicomm.speaker.device`（Unisound） | 顶部按键、TTS、配网、开机提示音 |
| `com.phicomm.speaker.player`（EchoService） | A2DP 状态机、DLNA、AirPlay、踢连接逻辑 |
| `com.phicomm.speaker.launcher` | 桌面 |
| `com.phicomm.speaker.systemtool` | 开机 BtMonitor（把扫描模式设成 20） |

---

## 2. 官方服务时间线

| 时间 | 事件 | 来源 |
|---|---|---|
| 2018 | 斐讯暴雷，R1 原价 2000+ 跌到几十元 | [19][20] |
| 2019-03/04 | 官方服务器关闭，App 无法登录、绑定失效 | [11]（二手来源，仅供参考） |
| 2020-05 | 社区用 DNS/hosts 劫持到第三方 IP，实现「半智能恢复」 | [10] |
| 2020–2021 | 免拆复活包、工具箱、微信小程序配网/点播出现 | [2][3][4][22] |
| 2026-07 | 「斐讯 R1 音乐 App V0.6.8」用原厂四麦 + 网易云/QQ 音乐 | [29] |
| 2026-09 | Armbian 移植跑通（Linux 6.18） | [28] |

> 现在原厂语音、音乐库、绑定都不可用；能用的都是本地功能或社区自建后端。

---

## 3. 按键与「模式」

| 当前状态 | 单击 | 双击 | 三击 | 长按 |
|---|---|---|---|---|
| 普通模式 | 唤醒/播放控制 | 休眠 [20] | **进入蓝牙模式** | **进入配网模式** |
| 蓝牙模式 | **退出蓝牙模式**（回普通模式） | 休眠 | 重置蓝牙状态 | **退出蓝牙 + 配网** |

代码依据（Unisound `com.phicomm.speaker.device`，实测反编译）：

- 三击 → `TripleClickProcessor.openBluetooth()` → `PhicommXController.openBlueToothStatus()` → `Message(what=64, arg1=1)`。
- 单击（蓝牙状态）→ `closeBlueToothStatus()` → `Message(64, 2)`；三击在蓝牙状态 → `resetBlueToothStatus()` → `Message(64, 3)`。
- 长按（蓝牙状态）→ `closeBlueToothStatus()` + `startConnectNet()` → `Message(0x40000, 1)` 进入配网；退出配网是 `Message(0x40000, 2)`。

> 社区也称三击为「打开蓝牙其实是同时打开蓝牙/DLNA/AirPlay」[2]。

---

## 4. 六条改造路线对比

| 路线 | 需要 | 能做什么 | 风险 | 可回滚 | 来源 |
|---|---|---|---|---|---|
| **A. 纯 ADB（本指南）** | 同网段 + adb | 静音开机音、保持蓝牙可连接、读写扫描模式/sysMode、装普通 APK | 低 | 全部可逆 | 实测 |
| B. 免拆换 APK / 工具箱 | ADB + 第三方包 | DLNA、AirPlay、氛围灯、绑定第三方服务器、音乐 App | 中：包来源不透明 | 部分（需备份原 APK） | [2][3][21][22][29] |
| C. 拆机 root | 拆机、焊 USB、短接 R211、U 盘 | 完整 root、改系统、删预装、控 LED | 高：焊坏/变砖 | 刷回备份 `out.img` | [14][15][16] |
| D. Armbian 移植 | 拆机焊 USB | Linux 6.18、Wi-Fi/蓝牙/AirPlay/四麦/独立救援 | 高 | 作者称有独立救援 | [28] |
| E. 硬件改 AUX | 丢弃原主板 | 纯有线音箱 | 不可逆 | 无 | [19] |
| **F. 小智 AI 语音（2026 主流）** | ADB + 3 个 APK（或 Janus 补丁 odex）+ 一台跑 Docker 的机器 | 原厂四麦唤醒 + 小智大模型对话 + 点歌/曲库/电台 + Web 控制台 + DLNA/AirPlay | 中：需 3448、需联网，第三方包来源需核验 | 卸载 APK 即还原，不动系统分区 | [38][39][40] |

> 本仓库只详细展开 A；B/C/D/F 给资料索引与风险提示，不提供一键脚本，也不附带固件。

---

## 5. 社区方案总表

| 项目 / 作者 | 形态 | 能力 | 依赖 | 许可 | 来源 |
|---|---|---|---|---|---|
| **phicomm-r1-controler** / OpenIoTHub | 电脑/NAS 上的 Go 程序 | 局域网 ADB 控制 R1，配合「云易连」App；内含「一键常开蓝牙DLNA.bat」 | adb、同网段 | MIT | [21] |
| **R1DiyTools** / CrazyBoyM | Windows 工具箱 | 配网、固件升级、装 DLNA、氛围灯 | adb | 未声明 | [22] |
| **R1ConfigWifi** / CrazyBoyM | 网页 | 免 App 配网 | 浏览器 + 作者服务器 | 未声明 | [23] |
| **r1-helper** / sagan | 装在 R1 的 APK | 英文 Alexa、蓝牙模式控制、LED/氛围灯、HTTP API(9000) | `pm hide` 系统包；root 控灯 | GPLv2 | [24] |
| **r1-webui** / yangyedan | Python Web 控制台 | 自建 AI 后端、DLNA 点播、状态/音量控制 | 改造版 APK + DLNA 渲染器 | MIT | [25] |
| **R1_DEVICE** / jojo729 | 改造版 APK | hook 原始语音命令，换用新 Unisound API | JDK 1.8+，需禁用 `device` | 未声明 | [26] |
| **R1up4Mac** / cyberty | Mac 脚本 | 本地 HTTP + ADB 升级 3119→3448 | Mac、adb | 未声明 | [27] |
| **Armbian 移植** / allinster | 完整 Linux | Linux 6.18、蓝牙/AirPlay/四麦唤醒/独立救援 | 拆机焊 USB | 见原帖 | [28] |
| **斐讯 R1 音乐 App V0.6.8** / 瀚思彼岸 | 一键.bat + 服务端 | 原厂四麦+播放器，网易云/QQ 音乐，`IP:8080` 管理，低音增强/氛围灯/蓝牙开关 | 自建 API、Docker | 未声明 | [29] |
| **微信小程序「R1 音箱遥控器」** | 小程序 | 配网、控制、点播 | 手机微信 | 第三方 | [4] |
| **一键安装免拆免 root 小讯.bat** | Windows 脚本 | 3448 上安装复活小讯 | adb | 转载包 | [2][3] |
| **new_EchoService 1.8.72 / AirReceiver / dlna.apk** | 替换/新增 APK | DLNA、AirPlay、绑定第三方服务器 | ADB | 转载包 | [2][30][32] |
| **乐播投屏 / AirReceiver** | APK | AirPlay/DLNA 接收 | ADB | 第三方 | [30] |
| **SoundWire** | PC 服务端 + R1 客户端 | 把 R1 当电脑无线音箱 | 同网段 | 第三方 | [31] |
| **小爱固件 2.4.1 / 2.10.24** | 拆机刷机 | 小爱同学 + 原厂四麦唤醒 | 拆机、刷机 | 社区固件 | [17][18][16] |
| **R1 救砖教程** | 教程 | 刷回原厂/救援 | 拆机、Ubuntu、U 盘 | — | [13] |
| **phicomm-r1-ota** / pexcn | OTA 描述文件 | 免拆逐级升级/降级固件（`otaprop.txt`） | adb | 未声明 | [48] |
| **Phicomm-R1_tools** / chln | Windows 批处理 | 老一代 adb 工具箱脚本 | adb | 未声明 | [49] |

**2026 年新增（语音/AI 方向）**：

| 项目 / 作者 | 形态 | 能力 | 依赖 | 许可 | 来源 |
|---|---|---|---|---|---|
| **Phicomm_R1_XiaoZhi_Client** / shj4588 | 3 个替换 APK + smali 源码 | 小智大模型对话、原厂四麦全链路（波束/降噪/AGC/AEC）、点歌/曲库/电台、`:8080` Web 控制台 | 3448 固件、Docker 跑音源 | 未声明 | [38][39] |
| **r1-xiaozhi** / xuan2261 | 装在 R1 的 APK（Java） | 小智唤醒词、云端/自建双模式、LED 状态、开机自启 | ADB；控灯需 root | 未声明 | [40] |
| **r1svc / Phicomm_R1_Proxy** / ctzjj | 本地 Python 后端 | DNS 劫持 `asrv3.hivoice.cn` 接管原厂语音链路；本地 ASR + LLM + 音乐代理，设备侧零改动 | Linux/WSL + Python | GPL-3.0 | [41] |
| **R1_Proxy** / minico | PHP 代理 | 保留原厂 App，挂 NAS 影音库 | PHP + NAS | GPL-3.0 | [42] |
| **phicomm-r1-voice-assistant** / Enter-tainer | APK + WebSocket 服务端 | 服务端唤醒词（openWakeWord）+ ASR + LLM + TTS，R1 只当麦克风/喇叭 | 局域网服务器 | 未声明 | [43] |
| **phicomm-r1-satellite** / sewellzhong | Python 卫星端 | Home Assistant 中文语音卫星，兼容 Android 5.1/ARMv7，ESPHome Native API，AEC 实测 | HA + 局域网 | Apache-2.0 | [44] |
| **assist-phicom-R1** / TriTue2011 | HACS 集成 | 把 R1 接进 Home Assistant 语音助手 | HA + HACS | MIT | [45] |
| **xiaozhi-android** / kuteo-git | 源码/构建脚本 | 在 R1（或任意 Android）上编译小智客户端 | Android Studio | 未声明 | [46] |
| **phicomm-r1-linux** / leohou666 | 设计文档/实验 | 自建 Linux：A2DP Sink（LDAC）、AirPlay、PipeWire、AK7755 DSP | 拆机 | 未声明（进行中） | [47] |

**视频与图文实战**（适合跟着一步步做）：

| 内容 | 形态 | 说明 | 来源 |
|---|---|---|---|
| 免拆复活小讯刷机 | B 站视频 | 全程不拆机，装复活小讯 | [33] |
| 一站式复活（联网/绑定/DLNA/小爱/氛围灯） | B 站视频 | 覆盖本文多条路线的串联演示 | [34] |
| 刷小爱同学保姆级教程 | YouTube 视频 | 拆机刷小爱固件全流程 | [35] |
| R1 最简使用指南 | 论坛长文 | 少折腾的日常用法思路 | [36] |
| 原版四麦 + 酷我音乐的源码方案 | 论坛源码帖 | 可自行编译，适合二次开发 | [37] |

**选型建议**：只为日常蓝牙 → A；要 DLNA/AirPlay → B（先备份原 APK）；要中文语音/音乐库 → [29]；要长期折腾/自建服务 → D。

---

## 6. 免拆免 root 实操（本机验证）

> 本仓库自己这台 R1 的最终配置、取舍与 v1.3 优化项，单独写在 **[OUR-SETUP.md](OUR-SETUP.md)**（实测记录 + 待验证项 + 回滚方法）。

### 6.1 工具链

- `adb`；Android SDK（`android.jar` API 21、`d8`、`zipalign`、`apksigner`）；JDK 17（编译用 `-source 8 -target 8`）。
- R1 上 `pm` 用 `/system/bin/pm`；`service call bluetooth` 不可用（5.1 只注册 `bluetooth_manager`）。

### 6.2 R1Buddy：开机静音 + 保持可连接

源码见 [`r1buddy/`](r1buddy/)。**仓库不再提供预编译 APK**：v1.1/v1.2 会在用户切出蓝牙模式后把它重新拉回来，v1.3 尚未在真机验证，都不适合直接发给别人；需要时按第 14 节自行编译。

1. `BOOT_COMPLETED` 时把 `STREAM_MUSIC` 音量置 0，25 秒后恢复（开机音是 Unisound 用媒体流播的 `bootloader_completed.mp3`）；
2. 开机 120 秒内扫描模式 23（可发现），之后回 21（可连接）；
3. 每 8 秒纠正异常扫描模式（**仅当 `sysMode=1` 时**）；A2DP 断开时只回 21（可连接），不再改成可发现。

```bash
adb connect <R1_IP>:5555
adb install -r R1Buddy-1.3.apk    # 自行编译的产物
adb shell am startservice -n com.r1.buddy/.GuardService
adb logcat -s R1Buddy
```

回滚：`adb shell /system/bin/pm uninstall com.r1.buddy`

> v1.2 曾在蓝牙被关时自动 `adapter.enable()`，会把用户刚切走的蓝牙重新打开；**v1.3 已删除这个动作**，并在正常模式（`sysMode=0`）下完全不干预。

### 6.3 切换模式（sysMode）

`helper/BtCtl.java` 编成 dex 推到 `/data/local/tmp/btctl.dex`：

```bash
CLASSPATH=/data/local/tmp/btctl.dex app_process /system/bin BtCtl status      # 查看
CLASSPATH=/data/local/tmp/btctl.dex app_process /system/bin BtCtl status 1    # 蓝牙模式（关 Wi-Fi，持久）
CLASSPATH=/data/local/tmp/btctl.dex app_process /system/bin BtCtl status 0    # 普通模式（Wi-Fi 回来）
CLASSPATH=/data/local/tmp/btctl.dex app_process /system/bin BtCtl off|on      # 关/开蓝牙
```

原理：`shell`（uid 2000）通过 `ServiceManager.getService("FXSystemPrivate")` + `ISysPrivate$Stub.asInterface()` 调 `getSysMode()` / `setSysMode(int)`，无需 root（实测）。

### 6.4 扫描模式单独控制

```bash
CLASSPATH=/data/local/tmp/getscan.dex app_process /system/bin GetScan
CLASSPATH=/data/local/tmp/scan.dex   app_process /system/bin SetScan 23
```

`21` 可连接，`23` 可发现，`20` NONE。反射调用隐藏 API `BluetoothAdapter.setScanMode(int,int)`。

### 6.5 日常取舍

| 需求 | 设置 | 结果 |
|---|---|---|
| 只当蓝牙音箱 | `sysMode=1` + R1Buddy | 开机即蓝牙模式；手机点一次已配对记录即可连接；**无 Wi-Fi** |
| 要联网/DLNA | `sysMode=0` + 蓝牙开启 | Wi-Fi/DLNA 可用；手机一连接就被 EchoService 断开 |
| 临时联网 | 蓝牙状态下单击按键 | 尝试退出蓝牙模式并恢复 Wi-Fi；v1.3 起 R1Buddy 不会再把它拽回蓝牙模式 |

---

## 7. 关键机制详解（本机实测）

### 7.1 手机「连上就被踢」

`EchoService` 的 `BluetoothPlayer$2.run()`（A2DP 广播接收器）：

```
Utils.isSystemInNormalMode(context) == true
  → BluetoothA2dpSink.disconnect(device)
```

`Utils.isSystemInNormalMode()` = `SysPrivateManager.getSysMode() == 0`。

日志实证：`BluetoothPlayer device: Redmi ... connected` 后 1 秒内出现 `BluetoothA2dpSink disconnect(...)`；`BluetoothPlayer.bootInit()` 还会在开机时断开所有已连接设备。

**所以三次按键的本质是把 `sysMode` 切成非 0 的蓝牙模式**，不是单纯「打开蓝牙」。

### 7.2 扫描模式

- 进入蓝牙模式后 EchoService 设 23（可发现），并启动 DLNA/AirPlay。
- 连接成功后 EchoService 设 20（NONE）；`systemtool` 的 BtMonitor 开机也设 20。
- R1Buddy 每 8 秒纠正 20→21（仅蓝牙模式），断开后只回 21，不再开放 23。

### 7.3 开机提示音

Unisound `PhicommInitializeHandler.playInitDoneTips()` → `UniMediaPlayer.playBeepSound("", 0x7f040001, ...)`，资源 `bootloader_completed.mp3`，流类型 **STREAM_MUSIC**（`setAudioStreamType(3)`）且 `setVolume(1.0, 1.0)`。

- `systemBootloader=false` 无效；`fxotamode` 读取抛 `IllegalStateException`；
- **静音媒体流 25 秒**即可压掉，不影响之后放音（R1Buddy）。

### 7.4 蓝牙模式会关 Wi-Fi

`sysMode=1` 后设备从局域网消失（ping/5555 均不可达），重启仍保持。APK 里只有配网工具和 `EasySetup.turnOnWifi` 会操作 Wi-Fi，说明「蓝牙模式关 Wi-Fi」在框架层。

### 7.5 消息号速查（Unisound → EchoService）

| what | arg1 | 含义 |
|---|---|---|
| 64 (0x40) | 1 | 进入蓝牙状态 |
| 64 | 2 | 退出蓝牙状态 |
| 64 | 3 | 重置蓝牙状态 |
| 262144 (0x40000) | 1 | 进入配网状态 |
| 262144 | 2 | 退出配网状态 |

`EchoService` 的 `IPlayer` AIDL 暴露 `startServer()` / `stopServer()`（日志 `CMD: startServer`），可作等效触发点，但**不会**改 `sysMode`。

---

## 8. 配网方式大全

| 方式 | 做法 | 依赖 | 来源 |
|---|---|---|---|
| **长按 + 热点 + 网页** | 长按进入配网，R1 开出无密码热点 `Phicomm_R1_xxx`；连接后浏览器打开 `http://106.12.194.27:19986/`，填 Wi-Fi 名/密码 | 第三方网页 | [5][6] |
| **微信小程序「R1 音箱遥控器」** | 微信搜索小程序，按其「音箱 WiFi 联网」引导配网 | 手机微信 | [2][4] |
| **R1ConfigWifi 网页** | 打开作者托管网页配网 | 作者服务器 | [23] |
| **声波配网** | 长按后由 App 用声音把 Wi-Fi 账号密码「播」给音箱 | 配套 App | [2] |
| **ADB 直接连 Wi-Fi** | 通过热点连上 R1 的 ADB，再用 `am start`/`svc` 或 `adbjoinwifi` 类 App 连指定 Wi-Fi | adb | [5][7][8] |
| **R1-join-wifi 脚本** | cyberty 的纯脚本方案，不装 App，直接让 R1 入网 | adb | [50] |
| **DNS/hosts 劫持** | 路由器 DNS 指到社区 IP（如 47.240.76.176），绕过「音箱未绑定」 | 路由器 | [10][12] |

> 重要：配网热点是**无密码开放热点**，电脑可以连进去；连上后 R1 通常有局域网 IP，可尝试 `adb connect <IP>:5555`。这既是配网通道，也是「音箱没有 Wi-Fi 时重新拿到 ADB」的后门。[5][6]

---

## 9. DLNA / AirPlay / 无线音频

| 方案 | 说明 | 来源 |
|---|---|---|
| 原厂 DLNA | 需「普通模式 + 蓝牙开启」；社区实测打开蓝牙后能看到两个 DLNA 设备 | [9] |
| new_EchoService + AirReceiver | 社区复活包，替换系统播放器并加 AirPlay/DLNA 接收 | [2][30][32] |
| 乐播投屏 | 装乐播 APK 实现 AirPlay 投送 | [30] |
| dlna.apk | 工具箱/一键脚本安装的 DLNA 服务端 | [22] |
| 音乐 App V0.6.8 | 原厂四麦+播放器，语音点歌，DLNA 播放，`IP:8080` 管理 | [29] |
| Home Assistant | 通过 DLNA 集成接入 HA，可做 TTS/媒体播放 | [32] |
| SoundWire | PC 端播放，R1 当无线音箱 | [31] |
| 多机串联 | 刷入串联文件后用电脑软件把多台 R1 组左右声道/立体声（走 Wi-Fi，不插线） | [51] |

---

## 10. Root、刷机、小爱、Armbian、救砖

### 10.1 拆机 root

- 材料：R1、电脑、数据线、U 盘、烙铁；Ubuntu 实体机（虚拟机可能不行）。[14][15]
- 步骤概要：拆机 → 焊 USB（黑 GND / 白 DM / 绿 DP）→ 短接 R211 到屏蔽盖 → 通电进 maskrom → 刷 `r1_root_rush.img`。[14][15]
- 备份：先备份 `out.img`，变砖后可刷回。[14]
- 风险：焊坏、短接失误、刷错包都会变砖。

### 10.2 刷小爱

- 社区固件有 2.4.1（音乐有唤醒）、2.10.24（原厂四麦唤醒）等版本。[17][18]
- 流程与 root 相同：拆机焊 USB → 刷固件 → 装小爱组件；部分包收费。[16][17]
- 注意：刷小爱后原厂小讯功能被替换，部分包需要付费激活。

### 10.3 Armbian（2026-09）

- 基于 Armbian 用户态 + Linux 6.18 内核；已跑通 eMMC、Wi-Fi、蓝牙、AirPlay、四麦唤醒、独立救援。[28]
- 仍需拆机焊 USB 刷机；作者称有独立救援方案，可回滚。

### 10.4 救砖

- 拆机短接进 maskrom，用 Rockchip 工具刷回备份/原厂包。[13][14]
- 社区救砖帖强调：先断电、短接 R211、用 Ubuntu 实体机、U 盘写盘工具 balenaEtcher。[13]

### 10.5 固件升级（免拆）

- 3119→3448 需逐级升级；`R1up4Mac` 用本地 HTTP 服务 + ADB 推送固件。[27]
- `phicomm-r1-ota` 提供各版本间 `otaprop.txt` 描述文件，推送后重启即可 OTA，免拆机。[48]
- 工具箱的「一键升级固件.bat」也可升级；过程断电有变砖风险。[2][22]

### 10.6 小智（Xiaozhi）AI 语音（2026 主流）

原厂云 2019 年就停了，2026 年社区的主流玩法是把 R1 变成小智 AI 音箱。三种实现，风险递增：

1. **替换 3 个 APK（免拆、可卸载还原）** —— shj4588 的方案，要求固件恰好是 **3448**：
   - 三个包：主包（顶替 `com.phicomm.speaker.device`）、控制台包（顶替 `com.phicomm.speaker.player`）、so 宿主包。[38][39]
   - 原理：不改系统、不刷分区，用 **Janus 漏洞（CVE-2017-13156）** 把客户端顶进原厂 speaker 进程；SELinux 域不变、四麦 HAL 原样保留，卸载即还原。[39]
   - 保留原厂四麦的波束成形/降噪/AGC/AEC，放歌时也能唤醒；`smali` 层已拆掉「未绑定/无网络/待升级」三道拦截。[39]
   - 能力：小智大模型对话（官方服务器或自建 `xiaozhi-esp32-server`）、点歌/歌手连播/随机/电台、自定义歌单 txt 导入、Web 控制台 `http://<R1-IP>:8080`（播放/歌词/音量/蓝牙/DLNA/AirPlay）、LED 氛围灯。[38][39]
   - 音源另需一台跑 Docker 的机器（群晖/飞牛/N1/软路由/迷你主机）。[39]
2. **装普通 APK** —— xuan2261 的 `r1-xiaozhi`：Java 应用，小智唤醒词、云端/自建双模式、开机自启，控灯需 root。[40]
3. **只接管后端、设备零改动** —— ctzjj 的 `r1svc`：DNS 劫持 `asrv3.hivoice.cn` 到本机，接管 ASR/NLU/音乐/聊天链路，音频不出内网。[41]

- 另有 UP 主做了「三系统」版本：同一台机器在 App 界面里切换小讯/小智/小爱，适合对比识别效果与音效。[52]

另有面向 Home Assistant 的路线：`phicomm-r1-satellite`（语音卫星，Apache-2.0）与 `assist-phicom-R1`（HACS 集成，MIT）。[44][45]

> ⚠️ 这三条都要 **Wi-Fi + 联网**，与本文路线 A 的「蓝牙模式（sysMode=1，系统会关 Wi-Fi）」互斥。切换前先想清楚要「手机蓝牙即连」还是「语音助手」；两者目前不能同时要。
>
> 回滚：`pm uninstall` 掉三个包（或 `pm uninstall --user 0`），或把 DNS 改回自动获取。全程不动 `out.img`。

---

## 11. 硬件改造

| 改造 | 做法 | 代价 | 来源 |
|---|---|---|---|
| AUX 有线 | 丢原主板，保留外壳/电源/喇叭，改 AUX 输入 | 失去语音/蓝牙/氛围灯，不可逆 | [19] |
| 加装 USB | 主板上预留 USB 触点，焊出 USB 口用于刷机 | 需拆机 | [14][16] |
| LED 控制 | 向 `/sys/class/leds/multi_leds0/led_color` 写 11 字节，如 `echo -n '7fff ffffff'` | 需 root + 关 SELinux | [24] |

> 拆解细节（四麦、导音锥、双被动辐射器）见 [20]。

---

## 12. FAQ

**Q：手机能配对，点连接马上又断开？**
A：`sysMode=0`。三击进入蓝牙模式，或 `BtCtl status 1`。注意会关 Wi-Fi。（实测）

**Q：开机声音很大，能关吗？**
A：能，静音媒体流（R1Buddy）。改设置/OTA 开关无效。（实测）

**Q：配网失败 / App 登录不了？**
A：官方服务器已停 [11]。用长按热点+网页 [5][6]、微信小程序 [4] 或 R1ConfigWifi [23]。

**Q：提示「音箱未绑定」？**
A：社区用 DNS/hosts 劫持到 47.240.76.176 解决，绑定成功后要改回 DNS。[10][12]

**Q：DLNA 不出现？**
A：需要普通模式 + 蓝牙开启 [9]；或装复活包/乐播 [30]。

**Q：氛围灯怎么开？**
A：原厂语音「打开氛围灯」，或社区复活包；免 root 只能装 App，控灯需 root [24][29]。

**Q：能升级固件吗？**
A：可以，3119→3448 逐级升级 [27]；3448 是官方末版 [1]。

**Q：怎么救砖？**
A：拆机短接进 maskrom 刷回备份 [13][14]。

**Q：R1 有几个版本？**
A：常见为 3119/3448 等固件版本；买二手建议确认 3448 [1][37]。

---

## 13. 回滚清单

```bash
# 卸载自建 App
adb shell /system/bin/pm uninstall com.r1.buddy

# 恢复普通模式（Wi-Fi 回来，蓝牙连接会被踢）
CLASSPATH=/data/local/tmp/btctl.dex app_process /system/bin BtCtl status 0

# 删除推送的 dex
adb shell rm /data/local/tmp/btctl.dex /data/local/tmp/scan.dex \
  /data/local/tmp/getscan.dex /data/local/tmp/sysmode.dex

# 若 hide 过系统包
adb shell /system/bin/pm unhide <包名>
```

进入蓝牙模式后也可**单击顶部按键**退出。

---

## 14. 覆盖范围与免责声明

**已覆盖**：GitHub 项目、恩山论坛、瀚思彼岸、CSDN、博客、B 站/YouTube 视频、拆解报告、二手交易经验；硬件、固件、root、免拆、Linux、语音后端（小智 / 本地 ASR+LLM / HA 卫星）、DLNA、救砖、配网、DNS 劫持、多机串联。

**未覆盖 / 不收录**：
- 百度网盘、QQ 群文件等无法验证的下载包；
- 收费固件/激活码的具体内容；
- 任何官方固件、系统 APK、第三方 APK 文件（版权与安全原因）；
- 抖音等短视频里的口头教程（仅作线索）。

**免责**：免 root 改动可逆，但模式切换会断网；拆机/root/刷机风险自负，可能变砖。不要冻结 `device`/`launcher`/`player`。第三方包可能含收费逻辑或后门，请自行核验。

---

## 15. 出处清单

1. 恩山论坛《斐讯R1全新开箱，联网，升级固件到3448，软件测试安装教程》—— https://www.right.com.cn/forum/thread-1953892-4-1.html
2. 04390《斐讯智能音箱R1免拆修复》—— https://www.04390.cn/news/1051.html
3. ZYX《斐讯R1免拆复活小讯小讯》—— https://www.zyx.company/1452.html
4. 恩山论坛《几万块买的斐讯R1完美复活，免拆免Root小讯+微信小程序控制和点播》—— https://www.right.com.cn/forum/thread-4091888-1-1.html
5. 恩山论坛《斐讯R1的ADB联网方式》（长按开无密码热点）—— https://www.right.com.cn/forum/thread-563259-1-1.html
6. CSDN / Destroyer_Dream《斐讯R1-搞机记录》（热点 + `106.12.194.27:19986` 配网页）—— https://blog.csdn.net/Destroyer_Dream/article/details/123759338
7. CSDN / laddie132《ADB控制安卓WIFI连接（斐讯R1联网指南）》—— https://blog.csdn.net/laddie132/article/details/89329246
8. CSDN / weixin_29172963《斐讯R1救星：ADB命令一键连接WiFi（附静态IP设置教程）》—— https://blog.csdn.net/weixin_29172963/article/details/159532906
9. powerSee《关于斐讯R1智能音响》（DLNA 与蓝牙的关系）—— https://powersee.github.io/2020/05/about-R1/
10. 恩山论坛《重大更新，斐讯R1 服务器已经半智能恢复了！》（DNS 47.240.76.176）—— https://www.right.com.cn/forum/thread-4009616-1-1.html
11. 酷番云问答《斐讯r1的服务器什么时候关闭的》（2019-03 关闭，二手来源）—— https://www.kufanyun.com/ask/647843.html
12. 博客园 / lome《斐讯R1智能音箱联网，以及解决音箱未绑定问题》—— https://www.cnblogs.com/lome/p/12465328.html
13. 恩山论坛《R1救砖教程-小白保姆级》—— https://www.right.com.cn/forum/thread-4063652-1-1.html
14. 星河博客《斐讯R1 root教程》（焊 USB、短接 R211、`r1_root_rush.img`）—— https://xinghevlog.github.io/2020/03/01/root/
15. zhigao5191《斐讯R1 root小白版教程【转】》—— http://www.zhigao5191.com/computer-network/R1rootjiaocheng.html
16. 博客园 / yanglang《斐讯R1魔改刷入小爱和ROOT固件教程》—— https://www.cnblogs.com/yanglang/articles/12580726.html
17. 恩山论坛《几万块买的斐讯R1完美复活，原厂四麦唤醒+最新小爱2.10.24固件》—— https://www.right.com.cn/forum/thread-7914296-1-1.html
18. ZYX《斐讯遗产R1音响刷小爱同学》（小爱固件 2.4.1 等）—— https://www.zyx.company/1130.html
19. CSDN / qq_41615718《原价两千多！现在一百块！斐讯R1爆改AUX有线音箱》—— https://blog.csdn.net/qq_41615718/article/details/143508456
20. 我爱音频网《拆解报告：斐讯AI智能音箱R1》—— https://www.52audio.com/archives/534.html
21. GitHub / OpenIoTHub《phicomm-r1-controler》—— https://github.com/IoTDevice/phicomm-r1-controler
22. GitHub / CrazyBoyM《R1DiyTools》—— https://github.com/CrazyBoyM/R1DiyTools
23. GitHub / CrazyBoyM《R1ConfigWifi》—— https://github.com/CrazyBoyM/R1ConfigWifi
24. GitHub / sagan《r1-helper》—— https://github.com/sagan/r1-helper
25. GitHub / yangyedan《r1-webui》—— https://github.com/yangyedan/r1-webui
26. GitHub / jojo729《R1_DEVICE》—— https://github.com/jojo729/R1_DEVICE
27. GitHub / cyberty《R1up4Mac》—— https://github.com/cyberty/R1up4Mac
28. 恩山论坛 / allinster《Armbian 移植：蓝牙、AirPlay、四麦、唤醒和独立救援已跑通》—— https://www.right.com.cn/forum/thread-8489524-1-1.html
29. 瀚思彼岸《将你的闲置斐讯R1用起来，支持网易云、QQ音乐听歌app来啦》（音乐 App V0.6.8）—— https://bbs.hassbian.com/thread-32696-1-1.html
30. 瀚思彼岸《斐讯R1 AI音响安装乐播，进行airplay投送的操作》—— https://bbs.hassbian.com/thread-5424-1-1.html
31. CSDN / weixin_39968490《挽救智障——斐讯R1:固件升级、安装DLNA和Soundwire》—— https://blog.csdn.net/weixin_39968490/article/details/111729367
32. 瀚思彼岸《斐讯R1音箱DLNA接入Home Assistant》—— https://bbs.hassbian.com/thread-24073-1-1.html
33. B 站《斐讯R1音箱手把手免拆复活小讯刷机教程》—— https://www.bilibili.com/video/BV1E3wLeBEbJ/
34. B 站《斐讯R1一站式复活教程，联网,绑定,DLNA,小爱同学,氛围灯》—— https://www.bilibili.com/video/av285839689/
35. YouTube《斐讯R1音响刷小爱同学，保姆级别教程》—— https://www.youtube.com/watch?v=JHVJuS7biOM
36. 恩山论坛《R1音箱最简单使用指南》—— https://www.right.com.cn/forum/thread-4017380-1-1.html
37. 恩山论坛《玩R1音响的源码，支持原版4麦方案，集成酷我音乐，可直接编译》—— https://www.right.com.cn/forum/thread-8284979-1-1.html
38. GitHub / shj4588《Phicomm_R1_XiaoZhi_Client》（V2.0.1 源码 + 原厂包 + 逆向资料）—— https://github.com/shj4588/Phicomm_R1_XiaoZhi_Client
39. 恩山论坛 / shj4588《【斐讯R1复活计划】秒杀原厂语音的小智AI音箱：固件+曲库+在线音源全开源教程》（2026-10-30）—— https://www.right.com.cn/forum/thread-8495552-1-1.html
40. GitHub / xuan2261《r1-xiaozhi》—— https://github.com/xuan2261/r1-xiaozhi
41. GitHub / ctzjj《Phicomm_R1_Proxy（r1svc）》GPL-3.0 —— https://github.com/ctzjj/Phicomm_R1_Proxy
42. GitHub / minico《R1_Proxy》GPL-3.0 —— https://github.com/minico/R1_Proxy
43. GitHub / Enter-tainer《phicomm-r1-voice-assistant》—— https://github.com/Enter-tainer/phicomm-r1-voice-assistant
44. GitHub / sewellzhong《phicomm-r1-satellite》Apache-2.0 —— https://github.com/sewellzhong/phicomm-r1-satellite
45. GitHub / TriTue2011《assist-phicom-R1》MIT（HACS 集成）—— https://github.com/TriTue2011/assist-phicom-R1
46. GitHub / kuteo-git《xiaozhi-android》—— https://github.com/kuteo-git/xiaozhi-android
47. GitHub / leohou666《phicomm-r1-linux》（Armbian/PipeWire/AK7755 实验）—— https://github.com/leohou666/phicomm-r1-linux
48. GitHub / pexcn《phicomm-r1-ota》（各版本 otaprop OTA 文件）—— https://github.com/pexcn/phicomm-r1-ota
49. GitHub / chln《Phicomm-R1_tools》—— https://github.com/chln/Phicomm-R1_tools
50. GitHub / cyberty《R1-join-wifi》GPL-3.0 —— https://github.com/cyberty/R1-join-wifi
51. B 站《斐讯r1音箱软件串联使用教程》（多机串联组立体声）—— https://www.bilibili.com/video/BV1wzh2zwEd1/
52. 抖音《给斐讯r1安装小智》（三系统切换：小讯/小智/小爱）—— https://www.douyin.com/shipin/7582405772399642666

> 注：论坛/博客链接可能随时间失效；固件与 APK 请自行核验来源与哈希，本仓库不提供下载。

---

## 16. 致谢

排名不分先后，感谢以下作者与社区的无私分享：

- **OpenIoTHub / IoTDevice**：phicomm-r1-controler（MIT）
- **CrazyBoyM**：R1DiyTools、R1ConfigWifi
- **sagan**：r1-helper（GPLv2）
- **yangyedan**：r1-webui（MIT）
- **jojo729**：R1_DEVICE
- **cyberty**：R1up4Mac
- **allinster**：R1 Armbian 移植
- **powerSee、星河（xinghevlog）、zhigao5191、lome、Destroyer_Dream、laddie132、weixin_29172963、weixin_39968490、qq_41615718、yanglang** 等博客作者
- **shj4588**：小智 AI 复活方案（3 包 + 逆向资料 + 全流程教程）
- **xuan2261、ctzjj、minico、Enter-tainer、sewellzhong、TriTue2011、kuteo-git、leohou666、pexcn、chln、cyberty**：2026 年语音/后端/Linux 方向的实现
- **恩山论坛、瀚思彼岸、我爱音频网、B 站/抖音/YouTube UP 主**及所有匿名分享者
- 斐讯 R1 硬件与固件的最初设计者（Unisound 语音、EchoService 播放器）

如果本文引用了你的内容而署名不全，请提 Issue，我会补充或删除。

---

## 17. 本仓库内容

```
r1-guide/
├─ README.md            # 本文
├─ OUR-SETUP.md         # 本项目的实测配置、取舍与回滚
├─ LICENSE              # 文档 CC BY-NC-SA 4.0 + 自建代码 MIT
├─ .gitignore
├─ r1buddy/             # 开机静音 + 保持蓝牙可连接的常驻 App（只放源码，不发 APK）
│  ├─ AndroidManifest.xml
│  └─ src/com/r1/buddy/GuardService.java
└─ helper/              # 免 root 小工具（app_process 运行）
   ├─ BtCtl.java        # 读取/切换 sysMode，开关蓝牙
   ├─ SysMode.java      # 仅读写 sysMode
   ├─ GetScan.java      # 读取扫描模式
   ├─ SetScan.java      # 设置扫描模式
   ├─ Bonds.java        # 列出已配对设备
   └─ exp-coexist.sh    # 验证「蓝牙模式下能否保持 Wi-Fi」的实验脚本（自带恢复）
```

编译 helper（Windows 示例，需 JDK 17 + Android build-tools）：

```bash
javac -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath <sdk>/platforms/android-21/android.jar \
  -d classes helper/BtCtl.java
d8 --min-api 21 --output dex classes/BtCtl.class
adb push dex/classes.dex /data/local/tmp/btctl.dex
```

编译 R1Buddy：用 Android Studio 或 `aapt` + `javac` + `d8` + `apksigner`，包名 `com.r1.buddy`，`minSdk 21`。
