<div align="center">

<br/>

<img src="docs/logo.png" alt="MC Host" width="160" />

# MC Host

### Server Minecraft langsung di HP Android

[English](README.md) · **Bahasa Indonesia** · [中文](README.zh.md) · [Русский](README.ru.md)

<br/>

[![Android](https://img.shields.io/badge/Android-8.0%2B%20arm64-3fa34a?style=for-the-badge&logo=android&logoColor=white&labelColor=0d1117)](#download)
[![Minecraft](https://img.shields.io/badge/Paper%20·%20Fabric%20·%20Forge%20·%20NeoForge-1.17%2B-5fd068?style=for-the-badge&labelColor=0d1117)](#fitur)
[![Java](https://img.shields.io/badge/Java-17%20·%2021%20·%2025%20bundled-e8efe9?style=for-the-badge&logo=openjdk&logoColor=white&labelColor=0d1117)](#fitur)
[![License](https://img.shields.io/badge/License-GPLv3-93a597?style=for-the-badge&labelColor=0d1117)](LICENSE)

<br/>

[**Download**](#download) · [**Fitur**](#fitur) · [**Mulai**](#mulai) · [**Main dari luar rumah**](#tunnel) · [**FAQ**](#faq) · [**Disclaimer**](#disclaimer) · [**Lisensi**](#lisensi)

</div>

> [!IMPORTANT]
> MC Host tidak berafiliasi dengan Mojang, Microsoft, PaperMC, FabricMC, Forge, NeoForged, Modrinth, playit.gg, maupun Cloudflare. Menjalankan server berarti kamu menyetujui [EULA Minecraft](https://aka.ms/MinecraftEULA).

---

<div align="center">

<img src="docs/banner.png" alt="MC Host banner" width="100%" />

<h1><a id="fitur"></a>Fitur</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### Server
- **Satu APK, tanpa Termux.** OpenJDK 17, 21, dan 25 untuk Android ikut di dalam APK, dipilih otomatis sesuai versi Minecraft.
- **Banyak server.** Simpan world modded 1.20.1 di samping server survival Paper 1.21, ganti dengan sekali tekan (satu yang jalan dalam satu waktu).
- **Enam jenis server:** Paper, Purpur, Fabric, Forge, NeoForge, dan Vanilla, Minecraft **1.17 sampai terbaru** (NeoForge mulai 1.20.2).
- **Wizard buat server:** pilih software, versi, nama, dan RAM, setujui EULA, selesai.
- **Start, stop, restart, kill** plus auto restart saat crash.
- **Tidur kalau sepi (opsional):** server berhenti setelah N menit tanpa pemain dan bangun sendiri saat ada yang join; alamatnya tetap sama.
- **Restart harian (opsional)** di jam pilihan, dengan hitung mundur di dalam game.
- **Buat dari modpack Modrinth (.mrpack):** loader, mod sisi server, dan config langsung siap.
- **Jalan di background** lewat foreground service, wake lock dan WiFi lock, bisa nyala otomatis saat app dibuka atau HP selesai boot.

#### Mod & Dunia
- **Store Modrinth** di dalam app: mod, plugin, dan datapack sesuai versi server, urutan, kategori, dependency ikut terpasang.
- **Paket optimasi HP** untuk Fabric sekali klik: Lithium, FerriteCore, Krypton, ModernFix, Chunky.
- **Kelola mod/plugin:** cari, nyalakan, matikan, hapus, upload `.jar`, lengkap dengan nama asli dan ikon dari Modrinth.
- **Update mod sekali tekan:** lihat mod mana yang punya versi baru untuk server kamu, update satu atau semua.
- **Backup & restore** dengan progress, backup otomatis selama server jalan, upload world dari PC, download backup.
- **File world:** jelajah folder server, ukuran Overworld/Nether/End, unduh, upload, ganti nama, hapus.
- **Editor config** untuk `.toml`, `.json`, `.properties` dan sejenisnya, plus form `server.properties` dengan penjelasan.
- **Reset Nether atau End** saja tanpa menghapus Overworld.
- **Backup server lengkap (opsional):** world plus mod, plugin, dan config; pulihkan utuh, jadikan server baru, atau import zip folder server.

    </td>
    <td width="50%" valign="top">

#### Panel
- **Panel web** di port `8080`, dibuka di app (WebView) atau browser HP/PC satu WiFi.
- **Bahasa Inggris, Indonesia, Mandarin, dan Rusia**, dengan tur pengenalan saat pertama dibuka.
- **Konsol live** dengan filter level, pencarian, riwayat command, tombol cepat, dan tombol difficulty.
- **Kelola pemain:** OP, whitelist, gamemode, heal, kick, ban, unban.
- **Pantau HP:** RAM server, uptime, baterai, suhu.
- **Notifikasi (opsional):** server siap, crash, tidur atau bangun, pemain masuk dan keluar, HP kepanasan.
- **Login PIN** untuk perangkat lain, salah 3x langsung dikunci sementara.
- **Backup config:** simpan pengaturan app (PIN, secret playit, domain dan token Cloudflare) lalu pulihkan setelah install ulang atau di HP baru.
- **Tampilan rapi** di HP maupun layar PC.

#### Akses dari internet
- **Tunnel playit.gg** bawaan (playitd dibuild untuk Android), gratis.
- **Pemain Bedrock (opsional):** satu sakelar memasang Geyser + Floodgate sesuai versi server, jadi pemain HP dan konsol bisa join.
- **Alamat tampil di tab Server**, tinggal salin dan kirim ke teman.
- **Domain sendiri via Cloudflare:** record SRV dihitung, dicek, dan bisa dibuat otomatis pakai API token.
- **Panduan langkah demi langkah** playit dan Cloudflare di tab Tunnel.

    </td>
  </tr>
</table>

<table>
  <tr>
    <td align="center"><img src="docs/screens/home.png" width="200" /><br/><sub>Server</sub></td>
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
adb install -r --bypass-low-target-sdk-block MCHost-1.0.x.apk
adb shell dumpsys deviceidle whitelist +com.minecrafthost.id
```

Update selalu aman: world, mod, dan pengaturan tidak hilang karena semua build ditandatangani dengan kunci yang sama.

**Pasang di Windows:** unduh `MCHost-1.0.x-win.msi` dari halaman [Releases](../../releases/latest) yang sama, lalu jalankan. Terpasang hanya untuk akun kamu, jadi tidak perlu hak admin. Tidak mau installer? Ekstrak `MCHost-1.0.x-win-portable.zip` di mana saja dan jalankan `MC Host.exe`.

- Windows 10 atau 11, 64-bit. Panel terbuka di jendelanya sendiri (memakai WebView2, bawaan Windows 11 dan Windows 10 yang terbaru).
- Menutup jendela tidak mematikan server, MC Host tetap jalan di tray. Klik kanan ikon tray untuk keluar; server dimatikan dengan rapi dulu.
- Java diunduh saat pertama kali server membutuhkannya, jadi tidak ada yang perlu dipasang lagi. Data tersimpan di `%LOCALAPPDATA%\MCHost`.
- Windows SmartScreen bisa memperingatkan installer karena belum bertanda tangan: klik **More info**, lalu **Run anyway**.
- Saat server pertama kali jalan, Windows Firewall menanyakan `java.exe`. Izinkan **Private networks** supaya teman di WiFi yang sama bisa join.
- Pindah antara HP dan PC: nyalakan backup server lengkap di Pengaturan, lalu pakai **Import server** di perangkat satunya.
- Seret file `.jar` ke panel untuk memasangnya sebagai mod atau plugin, atau `.zip` untuk impor server atau restore backup world.

---

<div align="center">

<h1><a id="mulai"></a>Mulai</h1>

</div>

1. Buka **MC Host**, izinkan notifikasi, pilih bahasa. Intro dan tur singkat menunjukkan semua menu.
2. Tekan **+** untuk membuat server: pilih software (Paper untuk plugin, Fabric/Forge/NeoForge untuk mod), versi Minecraft, nama, dan RAM (HP 8 GB: 3 sampai 4 GB), lalu setujui EULA. Software langsung diunduh.
3. Server Fabric? Buka **Mod > Store Modrinth**, tekan **Pasang semua** di Paket optimasi HP.
4. Di tab **Server** tekan **Start**. Start pertama lebih lama karena world dibuat dulu.
5. Teman satu WiFi join ke alamat **LAN** di tab Server.
6. Di **Pengaturan**, tekan **Matikan optimasi baterai**, lalu di info aplikasi nyalakan **Autostart** (MIUI/HyperOS) supaya server tidak dibunuh Android.

Buka panel dari PC: ketik alamat **PANEL** di browser, contoh `http://192.168.1.36:8080`, lalu masukkan PIN dari **Pengaturan > Akses panel**.

---

<div align="center">

<h1><a id="tunnel"></a>Main dari luar rumah</h1>

</div>

Panduan lengkap bergambar ada di app: tab **Tunnel > Bantuan**. Ringkasnya:

| Langkah | playit.gg (wajib, gratis) | Cloudflare (opsional) |
|---|---|---|
| 1 | Daftar di [playit.gg](https://playit.gg), tambah agent tipe **Docker**, salin `SECRET_KEY` | Domain sudah aktif di Cloudflare |
| 2 | Tempel di tab **Tunnel**, **Simpan & hubungkan**, **Start tunnel** | Buat API token template **Edit zone DNS** + izin **Zone · Read** |
| 3 | Di dashboard playit: tunnel **Minecraft Java** ke `127.0.0.1:25565` | Isi domain dan token di kartu **Domain Cloudflare** |
| 4 | Alamat muncul di tab **Server**, tekan salin | **Perbarui record sekarang**, lalu **Cek DNS** |

Setelah itu teman cukup join ke `xxx.joinmc.link` atau `mc.domainkamu.com`. Nyalakan **Whitelist** di tab Konsol supaya cuma teman yang bisa masuk.

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

Bisa. Di **Dunia > Settings**, matikan **Online mode**, lalu nyalakan **Whitelist** dan tambahkan nama teman di tab Konsol. Tanpa whitelist, siapa pun bisa masuk memakai nama orang lain.
</details>

<details>
<summary><b>Di log muncul "moved too quickly" padahal cuma lari</b></summary>

Itu pengecekan gerak bawaan Minecraft, bukan bug. Kalau koneksi (misalnya lewat tunnel playit) tersendat satu atau dua detik, gerakanmu terkirim sekaligus dan terbaca seperti lompat lebih dari 10 blok, jadi server menarikmu balik sedikit. Main lewat alamat LAN menghindarinya. Kalau di log juga ada "Can't keep up!", berarti HP-nya yang kewalahan.
</details>

<details>
<summary><b>Server lag atau HP panas?</b></summary>

Pasang Paket optimasi HP, turunkan *view distance* ke 5 sampai 6, pre-generate world dengan `chunky radius 1000` lalu `chunky start`, dan pakai WiFi, bukan data seluler. Hindari main dan host di HP yang sama untuk server dengan banyak mod.
</details>

---

<div align="center">

<h1><a id="disclaimer"></a>Disclaimer</h1>

</div>

MC Host itu project iseng: dibuat buat seru-seruan di waktu luang, supaya bisa main Minecraft bareng teman pakai HP yang nganggur. Ini bukan layanan hosting komersial dan tidak ada yang dibayar untuk mendukungnya. Fitur bisa berubah, kadang rusak di versi baru, dan tidak ada garansi atau jaminan server selalu nyala.

MC Host ditujukan untuk pemakaian pribadi yang sah: main bareng teman, belajar cara kerja server, dan eksperimen di perangkat milik sendiri. Kamu yang menjalankan server, jadi kamu yang menentukan siapa yang boleh masuk dan apa yang terjadi di dalamnya. Jaga PIN panel, pakai whitelist, simpan backup world yang penting, dan pantau suhu HP.

Ketentuan ini berlaku untuk semua versi, build, fork, dan saluran distribusi MC Host; setiap salinan tetap membawa lisensinya.

---

<div align="center">

<h1><a id="lisensi"></a>Lisensi</h1>

</div>

**GPL-3.0-or-later**

Kode MC Host berlisensi [GNU GPL v3](LICENSE) atau versi yang lebih baru. Materi pihak ketiga tetap memakai lisensi dan hak ciptanya sendiri; APK ikut membawa OpenJDK (GPLv2 dengan Classpath Exception) dan playitd (BSD 2-Clause), teks lengkapnya di [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt).

Kamu harus punya Minecraft dan menyetujui [EULA Minecraft](https://aka.ms/MinecraftEULA) untuk menjalankan server. MC Host tidak membundel Minecraft: Vanilla, Paper, Purpur, Fabric, Forge, dan NeoForge diunduh di HP dari sumber resmi dan tetap mengikuti lisensinya, begitu juga mod dan plugin dari Modrinth mengikuti lisensi pembuatnya. MC Host tidak memberi lisensi, akun, atau hak apa pun atas Minecraft. Versi bajakan, akun curian, dan mengakali ketentuan lisensi Mojang maupun pihak lain tidak diizinkan. Panduan ini tidak mengubah GPL maupun lisensi pihak ketiga.

Secret playit dan token Cloudflare disimpan di penyimpanan privat app dan hanya dikirim ke api.playit.gg dan api.cloudflare.com.

<div align="center">
<br/>
</div>
