#!/system/bin/sh
# 在 R1 本机运行：验证「蓝牙模式(sysMode=1) 下能否重新打开 Wi-Fi」。
# 必须用 nohup 后台跑，因为切 sysMode 时 Wi-Fi 会掉，ADB 会断。
# 自带恢复：若最终仍无 IP，则切回 sysMode=0 并重开 Wi-Fi。
log=/data/local/tmp/exp-coexist.log
: > $log

DEX=/data/local/tmp
bt() { CLASSPATH=$DEX/btctl.dex app_process /system/bin BtCtl "$@" 2>&1; }
wifi() { CLASSPATH=$DEX/wifictl.dex app_process /system/bin WifiCtl "$@" 2>&1; }
ipaddr() { getprop dhcp.wlan0.ipaddress; }

state() {
    echo "--- $(date +%H:%M:%S) ---" >> $log
    echo "wifi_on=$(settings get global wifi_on) ip=$(ipaddr)" >> $log
    ifconfig wlan0 2>/dev/null | grep -E 'inet addr|UP' >> $log
    bt status >> $log
    wifi status >> $log
}

echo "=== start ===" >> $log
state

echo "=== step1: set sysMode=1 ===" >> $log
bt status 1 >> $log
sleep 10
state

echo "=== step2: svc wifi enable ===" >> $log
svc wifi enable >> $log 2>&1
sleep 20
state

echo "=== step3: settings wifi_on=1 + svc enable ===" >> $log
settings put global wifi_on 1 >> $log 2>&1
svc wifi enable >> $log 2>&1
sleep 20
state

echo "=== step4: IWifiManager.setWifiEnabled(true) ===" >> $log
wifi on >> $log
sleep 25
state

ip=$(ipaddr)
if [ -z "$ip" ] || [ "$ip" = "0.0.0.0" ]; then
    echo "=== RESULT: FAIL (no ip in sysMode=1) -> recover ===" >> $log
    bt status 0 >> $log
    svc wifi enable >> $log 2>&1
    sleep 20
    state
else
    echo "=== RESULT: OK wifi alive in sysMode=1 (ip=$ip) ===" >> $log
    echo "下一步：用手机连蓝牙，确认音频与网络可同时工作" >> $log
fi
echo "=== done ===" >> $log
