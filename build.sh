#!/bin/bash
# 无 Gradle 构建（需 Android SDK build-tools 37 + platform android-35 + JDK 17）
# 依赖 jar: libs/libxposed-api-102.jar（compileOnly，框架运行时提供）
set -e
SDK=${SDK:-$LOCALAPPDATA/Android/Sdk}
BT="$SDK/build-tools/37.0.0"
AJ="$SDK/platforms/android-35/android.jar"
mkdir -p build/obj build/dex
javac -encoding UTF-8 -cp "$AJ;libs/libxposed-api-102.jar" -d build/obj src/com/lolipop/versionhook/*.java
"$BT/d8.bat" --release --lib "$AJ" --min-api 31 --output build/dex build/obj/com/lolipop/versionhook/*.class
"$BT/aapt2.exe" compile --dir res -o build/res.zip
"$BT/aapt2.exe" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml build/res.zip \
  --min-sdk-version 31 --target-sdk-version 34
python - << 'PY'
import zipfile
with zipfile.ZipFile('build/base.apk','a') as apk:
    apk.write('build/dex/classes.dex','classes.dex')
    apk.write('build/config.properties','config.properties')
    for f in ['META-INF/xposed/java_init.list','META-INF/xposed/module.prop','META-INF/xposed/scope.list']:
        apk.write(f, f)
PY
"$BT/zipalign.exe" -f -p 4 build/base.apk build/aligned.apk
"$BT/apksigner.bat" sign --ks <你的keystore> --ks-pass pass:*** --ks-key-alias <alias> --out VersionHook.apk build/aligned.apk
echo OK
