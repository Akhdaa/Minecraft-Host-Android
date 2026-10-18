<div align="center">

<br/>

<img src="docs/logo.png" alt="MC Host" width="160" />

# MC Host

### Server Minecraft langsung di HP Android

[English](README.md) · **Bahasa Indonesia**

<br/>

[![Android](https://img.shields.io/badge/Android-8.0%2B%20arm64-3fa34a?style=for-the-badge&logo=android&logoColor=white&labelColor=0d1117)](#download)
[![Minecraft](https://img.shields.io/badge/Paper%20·%20Fabric%20·%20Forge%20·%20NeoForge-1.17%2B-5fd068?style=for-the-badge&labelColor=0d1117)](#fitur)
[![Java](https://img.shields.io/badge/Java-17%20·%2021%20·%2025%20bundled-e8efe9?style=for-the-badge&logo=openjdk&logoColor=white&labelColor=0d1117)](#fitur)
[![License](https://img.shields.io/badge/License-GPLv3-93a597?style=for-the-badge&labelColor=0d1117)](LICENSE)

<br/>

[**Download**](#download) · [**Fitur**](#fitur) · [**Mulai**](#mulai) · [**Main dari luar rumah**](#tunnel) · [**FAQ**](#faq) · [**Lisensi**](#lisensi)

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
- **Jalan di background** lewat foreground service, wake lock dan WiFi lock, bisa nyala otomatis saat app dibuka atau HP selesai boot.

#### Mod & Dunia
- **Store Modrinth** di dalam app: mod, plugin, dan datapack sesuai versi server, urutan, kategori, dependency ikut terpasang.
- **Paket optimasi HP** sekali klik: Lithium, FerriteCore, Krypton, ModernFix, Chunky.
- **Kelola mod/plugin:** cari, nyalakan, matikan, hapus, upload `.jar`.
- **Backup & restore** dengan progress, backup otomatis selama server jalan, upload world dari PC, download backup.
- **File world:** jelajah folder server, ukuran Overworld/Nether/End, unduh, upload, ganti nama, hapus.
- **Editor config** untuk `.toml`, `.json`, `.properties` dan sejenisnya, plus form `server.properties` dengan penjelasan.
- **Reset Nether atau End** saja tanpa menghapus Overworld.

    </td>
    <td width="50%" valign="top">

#### Panel
- **Panel web** di port `8080`, dibuka di app (WebView) atau browser HP/PC satu WiFi.
- **Bahasa Inggris dan Indonesia**, dengan tur pengenalan saat pertama dibuka.
- **Konsol live** dengan filter level, pencarian, riwayat command, tombol cepat, dan tombol difficulty.
- **Kelola pemain:** OP, whitelist, gamemode, heal, kick, ban, unban.
- **Pantau HP:** RAM server, uptime, baterai, suhu.
- **Login PIN** untuk perangkat lain, salah 3x langsung dikunci sementara.
- **Tampilan rapi** di HP maupun layar PC.

#### Akses dari internet
- **Tunnel playit.gg** bawaan (playitd dibuild untuk Android), gratis.
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
adb shell dumpsys deviceidle whitelist +id.akhmad.mchost
```

Update selalu aman: world, mod, dan pengaturan tidak hilang karena semua build ditandatangani dengan kunci yang sama.

---

<div align="center">

<h1><a id="mulai"></a>Mulai</h1>

</div>

1. Buka **MC Host**, izinkan notifikasi, pilih bahasa. Intro dan tur singkat menunjukkan semua menu.
2. Tekan **+** untuk membuat server: pilih software (Paper untuk plugin, Fabric/Forge/NeoForge untuk mod), versi Minecraft, nama, dan RAM (HP 8 GB: 3 sampai 4 GB), lalu setujui EULA. Software langsung diunduh.
3. Server mod? Buka **Mod > Store Modrinth**, tekan **Pasang semua** di Paket optimasi HP.
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
| 4 | Alamat muncul di tab **Server**, tekan salin | **Buat / update record sekarang**, lalu **Cek DNS** |

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

<h1><a id="lisensi"></a>Lisensi</h1>

</div>

Kode MC Host berlisensi [GNU GPL v3](LICENSE) atau versi yang lebih baru (GPL-3.0-or-later). Kamu bebas memakai, mengubah, dan membagikan ulang, termasuk menjual, asalkan versi yang kamu sebarkan juga open source dengan lisensi yang sama dan menyertakan kode sumbernya. APK hasil build ikut membawa OpenJDK (GPLv2 dengan Classpath Exception) dan playitd (BSD 2-Clause), rinciannya di [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt).

* **Tidak membundel Minecraft.** Server Vanilla, Paper, Purpur, Fabric, Forge, dan NeoForge diunduh di HP dari sumber resmi saat install.
* **Mod tetap milik pembuatnya.** Mod dari Modrinth mengikuti lisensi masing-masing.
* **Token dan secret hanya di HP kamu.** Secret playit dan token Cloudflare disimpan di penyimpanan privat app dan hanya dikirim ke api.playit.gg dan api.cloudflare.com.

<div align="center">
<br/>
</div>
