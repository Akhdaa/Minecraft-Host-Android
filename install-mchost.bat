@echo off
setlocal
title Install MC Host ke HP
cd /d "%~dp0"

where adb >nul 2>nul || (
  echo [X] adb tidak ditemukan. Install Android platform-tools dan masukkan ke PATH:
  echo     https://developer.android.com/tools/releases/platform-tools
  pause & exit /b 1
)

echo [1/5] Download APK terbaru dari GitHub...
where gh >nul 2>nul && (
  del /q MCHost-*.apk 2>nul
  gh release download latest -R Akhdaa/Minecraft-Host-Android -p "MCHost-*.apk" --clobber
) || (
  echo [!] gh tidak ada atau belum login. Download manual MCHost-1.0.x.apk dari:
  echo     https://github.com/Akhdaa/Minecraft-Host-Android/releases/tag/latest
  echo     lalu taruh di folder yang sama dengan file .bat ini.
  start "" https://github.com/Akhdaa/Minecraft-Host-Android/releases/tag/latest
  pause
)
set "APK="
for /f "delims=" %%F in ('dir /b /o-d MCHost-*.apk 2^>nul') do if not defined APK set "APK=%%F"
if not defined APK (
  echo [X] File MCHost-*.apk tidak ada di folder ini.
  pause & exit /b 1
)
echo     APK: %APK%

echo [2/5] Menunggu HP (izinkan USB debugging di layar HP kalau muncul)...
adb wait-for-device

echo [3/5] Install / update...
adb install -r "%APK%"
if errorlevel 1 (
  echo [!] Coba lagi dengan bypass target SDK lama...
  adb install -r --bypass-low-target-sdk-block "%APK%"
  if errorlevel 1 (
    echo [X] Install gagal. Di HyperOS/MIUI aktifkan "Instal via USB" di Opsi pengembang.
    pause & exit /b 1
  )
)

echo [4/5] Matikan optimasi baterai dan izinkan jalan di background...
adb shell dumpsys deviceidle whitelist +id.akhmad.mchost >nul
adb shell cmd appops set id.akhmad.mchost RUN_ANY_IN_BACKGROUND allow >nul 2>nul

echo [5/5] Membuka MC Host...
adb shell am start -n id.akhmad.mchost/id.mchost.MainActivity >nul

echo.
echo Selesai. Tunggu "Java runtime siap" di aplikasi, lalu buka tab Server untuk install Fabric.
pause
