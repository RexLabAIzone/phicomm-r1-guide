#!/system/bin/sh
# Runs ON the R1. Tests whether Wi-Fi can stay on in Bluetooth mode (sysMode=1).
# Self-recovers: if Wi-Fi is still down after the test, sets sysMode=0 and re-enables Wi-Fi.
log=/data/local/tmp/exp-coexist.log
: > $log

bt() { CLASSPATH=/data/local/tmp/btctl.dex app_process /system/bin BtCtl "$@" 2>&1; }
state() {
    echo "--- state $(date +%H:%M:%S) ---" >> $log
    echo "wifi_on=$(settings get global wifi_on)" >> $log
    echo "ip=$(getprop dhcp.wlan0.ipaddress)" >> $log
    ifconfig wlan0 2>/dev/null | grep -E 'inet addr|UP' >> $log
    bt status >> $log
}

echo "=== start ===" >> $log
state
echo "=== set sysMode=1 ===" >> $log
bt status 1 >> $log
sleep 8
state
echo "=== wifi enable #1 ===" >> $log
svc wifi enable >> $log 2>&1
sleep 20
state
echo "=== wifi enable #2 ===" >> $log
svc wifi enable >> $log 2>&1
sleep 20
state

ip=$(getprop dhcp.wlan0.ipaddress)
if [ -z "$ip" ] || [ "$ip" = "0.0.0.0" ]; then
    echo "=== RECOVER: wifi failed in sysMode=1, back to sysMode=0 ===" >> $log
    bt status 0 >> $log
    svc wifi enable >> $log 2>&1
    sleep 15
    state
else
    echo "=== RESULT: wifi stayed on in sysMode=1 (ip=$ip) ===" >> $log
fi
echo "=== done ===" >> $log
