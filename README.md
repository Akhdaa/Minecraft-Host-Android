# MC Host

APK Android all in one buat hosting server Minecraft **Fabric** langsung di HP (target: POCO F4, arm64). Nggak butuh Termux.

Isi APK:
* Java 17 (OpenJDK build Termux untuk bionic, diambil otomatis waktu CI build)
* Web panel di port `8080`, dibuka lewat WebView di app atau browser HP/laptop lain di router yang sama (login pakai PIN)
* `playitd` (tunnel playit.gg) dibuild dari source untuk Android, opsional

## Build lewat GitHub Actions

1. Bikin repo baru (boleh private), upload semua isi folder ini, push ke branch `main`.
2. Tab **Actions** jalan otomatis (sekitar 10 sampai 20 menit, build pertama paling lama karena compile playitd).
3. APK ada di **Releases** (`MCHost-latest.apk`) dan di artifact workflow.

Build manual dengan pilihan JDK: Actions, **Build APK**, **Run workflow**. Pakai `openjdk-21` kalau mau main MC 1.20.5 ke atas.

Signing: default pakai `keystore/mchost.jks` (password `mchost123`) supaya tiap build bisa update tanpa uninstall. Mau kunci sendiri: isi secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Pakai

1. Install APK, buka, izinkan notifikasi. Tunggu "Java runtime siap" (ekstrak sekali, sekitar 1 menit).
2. Tab **Server**: pilih Minecraft `1.20.1`, loader terbaru, **Install**. Nyalakan **Setuju EULA**. Atur RAM (3 sampai 4 GB).
3. Tab **Mods**: **Pasang semua** paket optimasi, atau cari mod di Modrinth (otomatis filter Fabric 1.20.1 dan pasang dependency).
4. Tab **Beranda**: **Start**. Start pertama download server vanilla dan library.
5. Join dari HP/PC lain di WiFi yang sama ke alamat LAN yang tampil di Beranda.
6. Di tab **Server** tekan **Matikan optimasi baterai**, dan di info aplikasi nyalakan autostart (MIUI/HyperOS).

## Main dari luar rumah (domain Cloudflare)

1. playit.gg, tambah agent tipe Docker, salin `SECRET_KEY`, tempel di tab **Tunnel**, Start.
2. Di dashboard playit bikin tunnel Minecraft Java ke `127.0.0.1:25565`.
3. Cloudflare DNS: record SRV `_minecraft._tcp.mc`, priority 0, weight 5, port dari playit, target hostname playit, DNS only.
4. Join ke `mc.domainkamu.com`.

## Struktur

```
app/src/main/java/id/akhmad/mchost/core/   logika panel (Java murni, bisa dites di PC)
app/src/main/java/id/akhmad/mchost/        Activity WebView, foreground service, boot receiver
app/src/main/assets/panel/index.html       UI web panel
scripts/fetch_termux_runtime.py            ambil + rapikan OpenJDK Termux jadi assets/runtime/jre.tar.gz
scripts/build_playit.sh                    cross compile playitd pakai NDK
```

`targetSdk` sengaja 28: Android hanya mengizinkan eksekusi binary dari folder data app (java, playitd) untuk target API 28 ke bawah, sama seperti Termux. Android akan menampilkan peringatan "dibuat untuk versi lama" saat install, itu normal.
