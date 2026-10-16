<div align="center">

<br/>

<img src="docs/logo.png" alt="MC Host" width="160" />

# MC Host

### Server Minecraft Fabric langsung di HP Android

<br/>

[![Android](https://img.shields.io/badge/Android-8.0%2B%20arm64-3fa34a?style=for-the-badge&logo=android&logoColor=white&labelColor=0d1117)](#download)
[![Minecraft](https://img.shields.io/badge/Fabric-1.20.1-5fd068?style=for-the-badge&labelColor=0d1117)](#fitur)
[![Java](https://img.shields.io/badge/Java-17%20bundled-e8efe9?style=for-the-badge&logo=openjdk&logoColor=white&labelColor=0d1117)](#cara-kerja)
[![License](https://img.shields.io/badge/License-MIT-93a597?style=for-the-badge&labelColor=0d1117)](LICENSE)

<br/>

[**Download**](#download) · [**Fitur**](#fitur) · [**Mulai**](#mulai) · [**Main dari luar rumah**](#tunnel) · [**Build sendiri**](#build) · [**FAQ**](#faq) · [**Lisensi**](#lisensi)

</div>

> [!IMPORTANT]
> MC Host tidak berafiliasi dengan Mojang, Microsoft, FabricMC, Modrinth, playit.gg, maupun Cloudflare. Menjalankan server berarti kamu menyetujui [EULA Minecraft](https://aka.ms/MinecraftEULA).

---

<div align="center">

<img src="docs/banner.png" alt="MC Host banner" width="100%" />

<h1><a id="fitur"></a>Fitur</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### Server
- **Satu APK, tanpa Termux.** OpenJDK 17 untuk Android ikut di dalam APK.
- **Install Fabric sekali klik**, pilih versi Minecraft dan loader.
- **Start, stop, restart, kill** plus auto restart saat crash.
- **RAM bebas diatur** sampai total RAM HP, dengan peringatan.
- **Jalan di background** lewat foreground service, wake lock dan WiFi lock.
- **Nyala otomatis** saat app dibuka atau saat HP selesai boot.

#### Mod & Dunia
- **Store Modrinth** di dalam app: populer, kategori, cari, dependency ikut terpasang.
- **Paket optimasi HP** sekali klik: Lithium, FerriteCore, Krypton, ModernFix, Chunky.
- **Kelola mod**: cari, nyalakan, matikan, hapus, upload `.jar`.
- **Backup & restore world**, upload world dari PC, download backup.
- **Editor server.properties** dengan penjelasan (termasuk online mode untuk pemain crack).

    </td>
    <td width="50%" valign="top">

#### Panel
- **Panel web** di port `8080`, dibuka di app (WebView) atau browser HP/PC satu WiFi.
- **Login PIN** untuk perangkat lain, salah 3x langsung dikunci sementara.
- **Konsol live** dengan riwayat command dan tombol cepat.
- **Kelola pemain**: OP, whitelist, gamemode, heal, kick, ban, unban.
- **Pantau HP**: RAM server, uptime, baterai, suhu.
- **Tampilan rapi** di HP maupun layar PC.
  

#### Akses dari internet
- **Tunnel playit.gg** bawaan (playitd dibuild untuk Android), gratis.
- **Alamat otomatis tampil** di Beranda, tinggal Salin dan kirim ke teman.
- **Domain sendiri via Cloudflare**: record SRV dihitung, dicek, dan bisa dibuat otomatis pakai API token.
- **Panduan langkah demi langkah** playit dan Cloudflare di tab Tunnel.

    </td>
  </tr>
</table>

<table>
  <tr>
    <td align="center"><img src="docs/screens/home.png" width="200" /><br/><sub>Beranda</sub></td>
    <td align="center"><img src="docs/screens/console.png" width="200" /><br/><sub>Konsol &amp; pemain</sub></td>
    <td align="center"><img src="docs/screens/store.png" width="200" /><br/><sub>Store Modrinth</sub></td>
    <td align="center"><img src="docs/screens/domain.png" width="200" /><br/><sub>Domain Cloudflare</sub></td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="download"></a>Download</h1>

Ambil `MCHost-1.0.x.apk` terbaru dari halaman [**Releases**](../../releases/latest). Setiap push ke `main` otomatis membuild APK baru lewat GitHub Actions.

</div>

**Install dari HP:** buka APK, izinkan *Install unknown apps*. Android akan bilang app dibuat untuk versi lama, itu normal (lihat [FAQ](#faq)).

**Install lewat ADB:** jalankan [`install-mchost.bat`](install-mchost.bat) di PC dengan HP tersambung USB debugging. Script itu mengunduh APK terbaru, memasang atau mengupdate, mematikan optimasi baterai, lalu membuka app. Atau manual:

```bat
adb install -r MCHost-1.0.x.apk
adb shell dumpsys deviceidle whitelist +id.akhmad.mchost
```

Update selalu aman: world, mod, dan pengaturan tidak hilang karena semua build ditandatangani dengan kunci yang sama.

---

<div align="center">

<h1><a id="mulai"></a>Mulai</h1>

</div>

1. Buka **MC Host**, izinkan notifikasi, tunggu tulisan *Java runtime siap* (ekstrak sekali, sekitar 1 menit).
2. Tab **Server**: pilih Minecraft `1.20.1`, loader terbaru, tekan **Install**. Nyalakan **Setuju EULA**, atur RAM (HP 8 GB: 3 sampai 4 GB).
3. Tab **Mods**: buka **Store Modrinth**, tekan **Pasang semua** di Paket optimasi HP.
4. Tab **Beranda**: tekan **Start**. Start pertama mengunduh server vanilla dan library.
5. Teman satu WiFi join ke alamat **LAN** di Beranda.
6. Tab **Server**: tekan **Matikan optimasi baterai**, lalu di info aplikasi nyalakan **Autostart** (MIUI/HyperOS) supaya server tidak dibunuh Android.

Buka panel dari PC: ketik alamat **Panel web (LAN)** di browser, contoh `http://192.168.1.36:8080`, lalu masukkan PIN dari tab **Server > Akses panel**.

---

<div align="center">

<h1><a id="tunnel"></a>Main dari luar rumah</h1>

</div>

Panduan lengkap bergambar ada di app: tab **Tunnel > Bantuan**. Ringkasnya:

| Langkah | playit.gg (wajib, gratis) | Cloudflare (opsional) |
|---|---|---|
| 1 | Daftar di [playit.gg](https://playit.gg), tambah agent tipe **Docker**, salin `SECRET_KEY` | Domain sudah aktif di Cloudflare |
| 2 | Tempel di tab **Tunnel**, **Simpan key**, **Start tunnel** | Buat API token template **Edit zone DNS** + izin **Zone · Read** |
| 3 | Di dashboard playit: tunnel **Minecraft Java** ke `127.0.0.1:25565` | Isi domain dan token di kartu **Domain Cloudflare** |
| 4 | Alamat muncul di **Beranda**, tekan Salin | **Buat / update record sekarang**, lalu **Cek DNS** |

Setelah itu teman cukup join ke `xxx.joinmc.link` atau `mc.domainkamu.com`. Nyalakan **Whitelist** di tab Konsol supaya cuma teman yang bisa masuk.

---

<div align="center">

<h1><a id="build"></a>Build sendiri</h1>

</div>

Fork repo ini, lalu push ke `main`. Workflow [`build.yml`](.github/workflows/build.yml) akan:

1. Mengambil paket `openjdk-17` Termux (aarch64, bionic) beserta dependency, merapikan symlink, mengganti `cacerts`, dan mengemasnya jadi `assets/runtime/jre.bin`.
2. Cross compile `playitd` untuk Android dengan NDK (opsional, kalau gagal APK tetap jadi tanpa tunnel).
3. Build APK release lalu menerbitkannya di Releases.

Jalankan manual dari **Actions > Build APK > Run workflow** untuk memilih `openjdk-21` (Minecraft 1.20.5 ke atas). Kunci signing bawaan ada di `keystore/`; ganti dengan milikmu lewat secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

<details>
<summary><b>Struktur project</b></summary>

```
app/src/main/java/id/akhmad/mchost/core/   logika panel: HTTP server, proses server, mod, backup, tunnel, Cloudflare (Java murni)
app/src/main/java/id/akhmad/mchost/        Activity WebView, foreground service, boot receiver
app/src/main/assets/panel/index.html       UI panel web
scripts/fetch_termux_runtime.py            ambil dan rapikan OpenJDK Termux
scripts/build_playit.sh                    cross compile playitd pakai NDK
install-mchost.bat                         installer ADB untuk Windows
```

</details>

---

<div align="center">

<h1><a id="faq"></a>FAQ</h1>

</div>

<details>
<summary><b>Kenapa Android bilang app ini dibuat untuk versi lama?</b></summary>

`targetSdk` sengaja 28. Android hanya mengizinkan app menjalankan program dari folder datanya sendiri (di sini `java` dan `playitd`) kalau target API 28 ke bawah. Termux memakai cara yang sama. Di Android 14 ke atas, kalau install ditolak, pakai `adb install --bypass-low-target-sdk-block`.
</details>

<details>
<summary><b>Perlu root?</b></summary>

Tidak. Semua jalan tanpa root, dan di HP yang sudah root (Magisk / KernelSU) app ini juga jalan normal tanpa perlu diberi akses root. Root hanya membantu hal di luar app, misalnya mencegah Android membunuh proses server saat RAM penuh atau mengatur governor CPU.
</details>

<details>
<summary><b>Teman pakai Minecraft crack, bisa join?</b></summary>

Bisa. Tab **Dunia**, matikan **Online mode**, lalu nyalakan **Whitelist** dan tambahkan nama teman di tab Konsol. Tanpa whitelist, siapa pun bisa masuk memakai nama orang lain.
</details>

<details>
<summary><b>Server lag atau HP panas?</b></summary>

Pasang Paket optimasi HP, turunkan *view distance* ke 5 sampai 6, pre-generate world dengan `chunky radius 1000` lalu `chunky start`, dan pakai WiFi, bukan data seluler. Hindari main dan host di HP yang sama untuk server dengan banyak mod.
</details>

---

<div align="center">

<h1><a id="lisensi"></a>Lisensi</h1>

</div>

Kode MC Host berlisensi [MIT](LICENSE). APK hasil build ikut membawa OpenJDK (GPLv2 dengan Classpath Exception) dan playitd (BSD 2-Clause), rinciannya di [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt).

* **Tidak membundel Minecraft.** Server vanilla dan Fabric diunduh di HP dari sumber resmi saat install.
* **Mod tetap milik pembuatnya.** Mod dari Modrinth mengikuti lisensi masing-masing.
* **Token dan secret hanya di HP kamu.** Secret playit dan token Cloudflare disimpan di penyimpanan privat app dan hanya dikirim ke api.playit.gg dan api.cloudflare.com.

<div align="center">
<br/>
</div>
