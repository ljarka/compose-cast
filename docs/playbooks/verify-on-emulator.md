# Playbook — verify on the emulator

Unit tests have missed every rendering bug so far; do this for anything visual.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
ADB=~/Library/Android/sdk/platform-tools/adb

# 1. Emulator — -gpu host matters (~20x faster; without it everything looks slow)
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 -gpu host -no-snapshot-save &
$ADB wait-for-device
until [ "$($ADB shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 1; done

# 2. Server (restart it after server-side changes: screens are rendered at start).
#    It logs every request ("200 OK: GET - /screens/main"); add COMPOSECAST_DATA_DIR=examples/demo
#    to serve the JSON screens instead of the DSL ones.
./gradlew :server-app:run > server.log 2>&1 &
until curl -s localhost:8080/health >/dev/null; do sleep 1; done

# 3. App
./gradlew :app-template:installDebug
#    right after install Android may show a system "Updating..." screen for a while: wait
$ADB shell am force-stop io.composecast.demo
$ADB shell am start -W -n io.composecast.demo/.MainActivity     # TotalTime ≈ 0.4 s with -gpu host
#    a second app side by side: another properties file (other app.applicationId), e.g.
#    ./gradlew :app-template:installDebug -Pcomposecast.appConfig=/tmp/other/compose-cast.properties
#    $ADB shell am start -n <that id>/io.composecast.demo.MainActivity   (activity class keeps the code package)

# 4. Click with Maestro (the project's UI automation tool; flows in app-template/maestro/)
maestro test app-template/maestro/smoke.yaml                     # launch, tap "Get started", back
maestro test -e APP_ID=<id> app-template/maestro/smoke.yaml      # an app built from another properties file
#    One-off taps: write a scratch flow (tapOn: "<text>", back, assertVisible: ...) and run it the same way.

# 5. Look
$ADB exec-out screencap -p > screen.png                          # open it
$ADB shell uiautomator dump /sdcard/u.xml && $ADB shell cat /sdcard/u.xml | grep -o 'text="[^"]*"'
# fallback without Maestro — tap a button by its text: center of its bounds from the dump
tap_text() {
  $ADB shell uiautomator dump /sdcard/u.xml >/dev/null
  set -- $($ADB shell cat /sdcard/u.xml | grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" \
    | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
  $ADB shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
tap_text "Get started"                                           # then: tap_text "Continue"
$ADB shell input keyevent KEYCODE_BACK                           # back stack must pop, not exit
$ADB logcat -d | grep 'FATAL EXCEPTION'                          # must be empty
$ADB logcat -d -s ComposeCast | grep REQUEST                     # app's HTTP requests (count reloads)
grep 'GET - /screens' server.log                                 # the same requests, server side
```

Clicking: **use Maestro** (`brew install maestro`, verified with 2.12.1). Buttons rendered by Remote
Compose are in the accessibility tree with their text, so `tapOn: "<label>"` and `assertVisible` work
on them. A run that ends right after "Flow …" with `DeviceServerDiedException` in
`~/.maestro/tests/<run>/maestro.log` is Maestro losing its device connection, not a failing
flow: rerun it. Add a flow to `app-template/maestro/` when a check is worth repeating. The `tap_text` adb
helper above is only a fallback.

Snippets are bash (`bash -c` from zsh: zsh does not word-split `$var`). Buttons rendered by Remote Compose appear in the dump with their text and bounds; re-dump after
each navigation. If the first screen shows only the loading spinner, take the screenshot a few seconds later
before suspecting the code. Performance doubts: `$ADB shell dumpsys gfxinfo io.composecast.demo`.
