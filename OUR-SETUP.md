# 我们自己的 R1 方案（实测记录）

> 这份文档记录**本仓库自己在这台 R1 上做的事**：为什么这么选、改了什么、怎么验证、怎么退回去。
> 背景资料、社区全部方案与出处见 [README.md](README.md)。设备：斐讯 R1 / RK322x / Android 5.1.1 / 固件 3448 / 无 root。

---

## 1. 目标与取舍

需求只有三条：**开机不吵、不用连按三次、手机能连上**。

社区六条路线里（README 第 4 节），能满足"每天当蓝牙音箱"且不拆机、不刷机的只有路线 A。代价是：

- `sysMode=1`（蓝牙模式）会让**系统自己关掉 Wi-Fi**，所以这台机器现在不联网；
- 小智/本地语音那套（路线 F）必须联网，且要求 3448 + 3 个第三方 APK，与当前状态**互斥**。

用户已确认接受"先不联网"，因此主线停在 A，不往 B/F 推进。

---

## 2. 当前生效的配置

| 项 | 值 | 怎么来的 |
|---|---|---|
| 固件 | 3448 | 原厂 |
| sysMode | **1**（蓝牙模式，重启保持） | `ISysPrivate.setSysMode(1)`，见 `helper/BtCtl.java` |
| Wi-Fi | 关闭 | 蓝牙模式的系统行为，非人为禁用 |
| 蓝牙 | 开机自动启用，扫描模式保持 21（可连接） | `R1Buddy` 每 20 秒纠正一次 |
| 开机提示音 | 无 | `R1Buddy` 开机把 STREAM_MUSIC 置 0，25 秒后恢复原值 |
| 连接方式 | 手机点一次已配对的 `Phicomm_R1_xxxx` | 手机侧发起 |
| 回滚 | `pm uninstall com.r1.buddy` + `setSysMode(0)` | 全部可逆 |

已实测：重启后自动进入蓝牙模式、无开机音、手机点一次即连；Wi-Fi 不恢复。

---

## 3. 为什么这样做（机制）

1. **不是扫描模式的问题** —— 手机连上瞬间会被 EchoService 主动踢掉：`BluetoothPlayer$2.run()` 里只要 `Utils.isSystemInNormalMode()`（`getSysMode()==0`）为真就 `BluetoothA2dpSink.disconnect(device)`。
2. **三次按键的本质** —— Unisound 收到三击 → 给 EchoService 发 msg 64 → 切到非 0 的蓝牙模式，并启动 DLNA/AirPlay。直接改 `sysMode` 等价于按了三次（但不启动 DLNA 服务）。
3. **扫描模式** —— EchoService 连接成功后会把扫描模式设成 20（NONE，不可连接），systemtool 的 BtMonitor 开机也设 20。所以必须有人把 21 纠正回来，否则断开后手机连不上。
4. **开机音** —— Unisound `UniMediaPlayer.playBeepSound` 走 STREAM_MUSIC，静音该流即可压掉；`systemBootloader=false`、`fxotamode=silent` 都无效（uid 10008 无权限）。
5. **不需要 root** —— `setScanMode(int,int)` 和 `ISysPrivate` 都能通过 `app_process` 以 shell 身份反射调用（`helper/`），常驻 App 也能调。

详细证据链见 README 第 7 节。

---

## 4. R1Buddy 设计

一个前台服务 + 一个开机广播，无界面、无网络权限。

| 参数 | 1.1（已验证） | 1.2（待验证） |
|---|---|---|
| 静音开始 | 开机 120 秒内 | 不变 |
| 静音恢复 | 25 秒后 / A2DP 连上时 | 不变 |
| 扫描模式纠正间隔 | 20 秒 | **8 秒** |
| 断开后进入可发现 | 是，60 秒后回 21 | 不变 |
| 开机主动回连 | 无 | **12/30/60 秒各试一次** |
| 记住最后连接的手机 | 无 | 有（`lastAddr`） |
| 手动触发回连 | 无 | `am startservice -a com.r1.buddy.RECONNECT` |

### 1.2 的两处改动及理由

- **主动回连**：R1 作为 A2DP Sink，通过隐藏的 A2DP Sink profile 代理调用 `connect(device)`，由音箱侧发起连接。已配对的 Android 手机通常会直接接受，从而省掉"手动点一次"。只在开机后 5 分钟内尝试，避免用户主动断开后被反复拉回。
- **纠正间隔 20s → 8s**：连接成功瞬间 EchoService 会把扫描模式设成 20（不可连接）。间隔越短，断开后手机能更快连回来。

> ⚠️ **1.2 尚未在真机验证**：R1 当前处于蓝牙模式（无 Wi-Fi），ADB 不可达。代码能编译、能打包、签名与 1.1 相同（SHA-256 `d1fc5c96…225bb6`，`CN=R1Buddy`），但回连是否成功、8 秒间隔是否引起抖动，需要在有网络或 USB 的条件下实测。

---

## 5. 验证 1.2（等 R1 能连 ADB 时）

```bash
adb connect <R1-IP>:5555

# 安装（-r 覆盖；签名相同，不丢数据）
adb install -r R1Buddy-1.2.apk

# 触发一次手动回连
adb shell am startservice -a com.r1.buddy.RECONNECT -n com.r1.buddy/.GuardService

# 看日志
adb logcat -s R1Buddy
```

要看的四条日志：

1. `A2DP sink proxy ready` —— profile 代理起来了（失败会打印 `getProfileProxy failed`）。
2. `reconnect XX:XX:... -> true` —— 回连请求被接受。
3. `A2DP connected` —— 手机确认连上。
4. `scan mode was 20, fix to 21` 频次 —— 若连接后反复出现，说明 EchoService 在抢，属正常，但不应导致声音中断。

验证点：重启 → 不动手机，看是否自动连上；若自动连上，则"手动点一次"这一步可以去掉。

若 `reconnect` 一直返回 `false`：可加一步兜底——先连 HFP（`HEADSET_CLIENT`，profile 16）把 ACL 链路拉起来，再连 A2DP。社区的蓝牙自动重连实现就是按 `HEADSET_CLIENT → A2DP_SINK` 的顺序依次尝试的；这一步先不写进代码，等真机确认 A2DP 直连失败再加。

### 回滚

```bash
# 退回已验证的 1.1（versionCode 更小，必须加 -d）
adb install -r -d R1Buddy.apk

# 或彻底卸载
adb shell pm uninstall com.r1.buddy
```

---

## 6. 手机侧备选（无需改 R1）

如果 1.2 的回连不稳，最省事的兜底是在手机做自动化：Tasker / MacroDroid 里加一条"检测到 `Phicomm_R1_xxxx` 在附近 → 连接 A2DP"，效果等同于"自动点一次"。

---

## 7. 已知边界

- 拦不住 EchoService 的 `bootInit()` 在开机时断开所有 A2DP 设备，只能在它之后重连。
- 改不了开机音的来源（Unisound 的 mp3 与播放调用），只能静音媒体流；若日后静音失效，说明播放流被改成别的 STREAM。
- 单次按键退出蓝牙模式（`sysMode=0`）会立刻恢复 Wi-Fi，同时手机再连就会被 EchoService 踢——这是原厂设计，不是故障。
- 系统升级或恢复出厂会清掉 `sysMode` 与已装的 App，按 README 第 13 节重做即可。

---

## 8. 和社区方案的关系

| 想要 | 用哪条 |
|---|---|
| 手机蓝牙即连、不折腾 | **本方案（A）** |
| 语音助手 / 小智 / 本地大模型 | 路线 F（`OUR` 以外，见 README 10.6），需 Wi-Fi，与 A 互斥 |
| DLNA / AirPlay | 路线 B（换 APK），可与 A 共存但需先恢复 Wi-Fi |
| 完整 Linux / 控灯 / 删预装 | 路线 C/D（拆机） |

本方案只用到 ADB 和普通 App 权限，社区里的 Janus 补丁、拆机刷机一律没用。
