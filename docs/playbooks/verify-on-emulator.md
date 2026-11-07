# Playbook — verify on the emulator

Unit tests have missed every rendering bug so far; do this for anything visual.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
ADB=~/Library/Android/sdk/platform-tools/adb

# 1. Emulator — -gpu host matters (~20x faster; without it everything looks slow)
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 -gpu host -no-snapshot-save &
$ADB wait-for-device
until [ "$($ADB shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 1; done

# 2. Server (restart it after server-side changes: screens are rendered at start)
./gradlew :server-app:run &
until curl -s localhost:8080/health >/dev/null; do sleep 1; done

# 3. App
./gradlew :app-template:installDebug
#    right after install Android may show a system "Updating..." screen for a while: wait
$ADB shell am force-stop io.composecast.demo
$ADB shell am start -W -n io.composecast.demo/.MainActivity     # TotalTime ≈ 0.4 s with -gpu host

# 4. Look and interact
$ADB exec-out screencap -p > screen.png                          # open it
$ADB shell uiautomator dump /sdcard/u.xml && $ADB shell cat /sdcard/u.xml | grep -o 'text="[^"]*"'
$ADB shell input tap 540 1064                                    # e.g. the "Get started" button (1080x2400)
$ADB shell input keyevent KEYCODE_BACK                           # back stack must pop, not exit
$ADB logcat -d | grep 'FATAL EXCEPTION'                          # must be empty
```

If the first screen shows only the loading spinner, take the screenshot a few seconds later
before suspecting the code. Performance doubts: `$ADB shell dumpsys gfxinfo io.composecast.demo`.
