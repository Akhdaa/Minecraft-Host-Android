<div align="center">

<br/>

<img src="docs/logo.png" alt="MC Host" width="160" />

# MC Host

### A Minecraft server running right on your Android phone

**English** · [Bahasa Indonesia](README.id.md)

<br/>

[![Android](https://img.shields.io/badge/Android-8.0%2B%20arm64-3fa34a?style=for-the-badge&logo=android&logoColor=white&labelColor=0d1117)](#download)
[![Minecraft](https://img.shields.io/badge/Paper%20·%20Fabric%20·%20Forge%20·%20NeoForge-1.17%2B-5fd068?style=for-the-badge&labelColor=0d1117)](#features)
[![Java](https://img.shields.io/badge/Java-17%20·%2021%20·%2025%20bundled-e8efe9?style=for-the-badge&logo=openjdk&logoColor=white&labelColor=0d1117)](#features)
[![License](https://img.shields.io/badge/License-GPLv3-93a597?style=for-the-badge&labelColor=0d1117)](LICENSE)

<br/>

[**Download**](#download) · [**Features**](#features) · [**Getting started**](#start) · [**Play over the internet**](#tunnel) · [**FAQ**](#faq) · [**License**](#license)

</div>

> [!IMPORTANT]
> MC Host is not affiliated with Mojang, Microsoft, PaperMC, FabricMC, Forge, NeoForged, Modrinth, playit.gg or Cloudflare. Running a server means you accept the [Minecraft EULA](https://aka.ms/MinecraftEULA).

---

<div align="center">

<img src="docs/banner.png" alt="MC Host banner" width="100%" />

<h1><a id="features"></a>Features</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### Server
- **One APK, no Termux.** OpenJDK 17, 21 and 25 for Android ship inside the APK; the right one is picked per Minecraft version.
- **Multiple servers.** Keep a modded 1.20.1 world next to a Paper 1.21 survival server and switch with one tap (one runs at a time).
- **Six server types:** Paper, Purpur, Fabric, Forge, NeoForge and Vanilla, Minecraft **1.17 to latest** (NeoForge from 1.20.2).
- **Create-server wizard:** pick software, version, name and RAM, accept the EULA, done.
- **Start, stop, restart, kill** plus auto restart on crash.
- **Runs in the background** with a foreground service, wake lock and Wi-Fi lock, and can start when the app opens or the phone boots.

#### Mods & World
- **Modrinth store** in the app: mods, plugins and datapacks for your server's version, sorting, categories, dependencies installed automatically.
- **Phone optimization pack** in one tap: Lithium, FerriteCore, Krypton, ModernFix, Chunky.
- **Manage mods/plugins:** search, enable, disable, delete, upload `.jar`.
- **Backup & restore** with progress, automatic backups while the server runs, upload a world from your PC, download backups.
- **World files:** browse the server folder, see Overworld/Nether/End sizes, download, upload, rename, delete.
- **Config editor** for `.toml`, `.json`, `.properties` and similar files, plus a guided `server.properties` form.
- **Reset only the Nether or the End** without touching the Overworld.

    </td>
    <td width="50%" valign="top">

#### Panel
- **Web panel** on port `8080`, inside the app (WebView) or in any browser on the same Wi-Fi.
- **English and Indonesian**, with a first-run tour of every screen.
- **Live console** with level filters, search, command history, quick commands and difficulty buttons.
- **Player management:** OP, whitelist, gamemode, heal, kick, ban, unban.
- **Phone stats:** server RAM, uptime, battery and temperature.
- **PIN login** for other devices; 3 wrong tries locks login for a while.
- **Looks right** on phones and on desktop screens.

#### Play over the internet
- **Built-in playit.gg tunnel** (playitd built for Android), free.
- **Addresses shown on the Server tab:** copy and send to friends.
- **Your own domain via Cloudflare:** the SRV record is computed, checked and can be created automatically with an API token.
- **Step-by-step guides** for playit and Cloudflare in the Tunnel tab.

    </td>
  </tr>
</table>

<table>
  <tr>
    <td align="center"><img src="docs/screens/home.png" width="200" /><br/><sub>Server</sub></td>
    <td align="center"><img src="docs/screens/console.png" width="200" /><br/><sub>Console &amp; players</sub></td>
    <td align="center"><img src="docs/screens/store.png" width="200" /><br/><sub>Modrinth store</sub></td>
    <td align="center"><img src="docs/screens/domain.png" width="200" /><br/><sub>Cloudflare domain</sub></td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="download"></a>Download</h1>

Get the latest `MCHost-1.0.x.apk` from [**Releases**](../../releases/latest). Every push to `main` builds a new APK with GitHub Actions.

</div>

**Install on the phone:** open the APK and allow *Install unknown apps*. Android will say the app was built for an older version; that's expected (see [FAQ](#faq)).

**Install with ADB:** run [`install-mchost.bat`](install-mchost.bat) on a PC with the phone connected over USB debugging. It downloads the latest APK, installs or updates it, turns off battery optimization and opens the app. Or by hand:

```bat
adb install -r --bypass-low-target-sdk-block MCHost-1.0.x.apk
adb shell dumpsys deviceidle whitelist +id.akhmad.mchost
```

Updates are always safe: worlds, mods and settings are kept because every build is signed with the same key.

---

<div align="center">

<h1><a id="start"></a>Getting started</h1>

</div>

1. Open **MC Host**, allow notifications, pick your language. The short intro and tour show you around.
2. Tap **+** to create a server: choose the software (Paper for plugins, Fabric/Forge/NeoForge for mods), the Minecraft version, a name and RAM (8 GB phone: 3 to 4 GB), and accept the EULA. The software downloads right away.
3. Modded server? Open **Mod > Modrinth store** and tap **Install all** on the phone optimization pack.
4. On the **Server** tab press **Start**. The first start takes longer while the world is generated.
5. Friends on the same Wi-Fi join the **LAN** address shown on the Server tab.
6. In **Settings**, tap **Disable battery optimization**, then enable **Autostart** in app info (MIUI/HyperOS) so Android doesn't kill the server.

To open the panel from a PC, type the **PANEL** address into a browser (for example `http://192.168.1.36:8080`) and enter the PIN from **Settings > Panel access**.

---

<div align="center">

<h1><a id="tunnel"></a>Play over the internet</h1>

</div>

A full illustrated guide is in the app under **Tunnel > Help**. In short:

| Step | playit.gg (required, free) | Cloudflare (optional) |
|---|---|---|
| 1 | Sign up at [playit.gg](https://playit.gg), add a **Docker** agent, copy its `SECRET_KEY` | Your domain is active on Cloudflare |
| 2 | Paste it in the **Tunnel** tab, **Save & connect**, **Start tunnel** | Create an API token from the **Edit zone DNS** template + **Zone · Read** |
| 3 | In the playit dashboard: a **Minecraft Java** tunnel to `127.0.0.1:25565` | Enter the domain and token in the **Cloudflare domain** card |
| 4 | The address appears on the **Server** tab, tap copy | **Create / update record now**, then **Check DNS** |

Friends then join `xxx.joinmc.link` or `mc.yourdomain.com`. Turn on the **Whitelist** in the Console tab so only your friends can get in.

---

<div align="center">

<h1><a id="faq"></a>FAQ</h1>

</div>

<details>
<summary><b>Why does Android say this app was built for an older version?</b></summary>

`targetSdk` is deliberately 28. Android only lets an app run programs from its own data folder (here `java` and `playitd`) when it targets API 28 or lower; Termux does the same. On Android 14 and later, if the install is blocked, use `adb install --bypass-low-target-sdk-block`.
</details>

<details>
<summary><b>Do I need root?</b></summary>

No. Everything works without root, and rooted phones (Magisk / KernelSU) work normally without granting root. Root only helps with things outside the app, such as stopping Android from killing the server when RAM is full or tuning the CPU governor.
</details>

<details>
<summary><b>Can friends with cracked Minecraft join?</b></summary>

Yes. In **World > Settings**, turn off **Online mode**, then turn on the **Whitelist** and add your friends in the Console tab. Without a whitelist anyone could join using someone else's name.
</details>

<details>
<summary><b>The log says "moved too quickly" when I just run</b></summary>

That's vanilla Minecraft's movement check, not a bug. When a connection (for example through the playit tunnel) stalls for a second or two, your movement arrives all at once and looks like a jump of more than 10 blocks, so the server pulls you back slightly. Playing on the LAN address avoids it. If the log also shows "Can't keep up!", the phone itself is overloaded.
</details>

<details>
<summary><b>Server lag or a hot phone?</b></summary>

Install the phone optimization pack, lower *view distance* to 5 or 6, pre-generate the world with `chunky radius 1000` then `chunky start`, and use Wi-Fi instead of mobile data. Avoid playing and hosting on the same phone for heavily modded servers.
</details>

---

<div align="center">

<h1><a id="license"></a>License</h1>

</div>

MC Host's code is licensed under the [GNU GPL v3](LICENSE) or later (GPL-3.0-or-later). You're free to use, modify and redistribute it, including selling it, as long as what you distribute is also open source under the same license and includes its source code. The APK bundles OpenJDK (GPLv2 with Classpath Exception) and playitd (BSD 2-Clause); details are in [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt).

* **Minecraft is not bundled.** Vanilla, Paper, Purpur, Fabric, Forge and NeoForge servers are downloaded on the phone from their official sources at install time.
* **Mods belong to their authors.** Mods from Modrinth follow their own licenses.
* **Tokens and secrets stay on your phone.** The playit secret and Cloudflare token are kept in the app's private storage and only sent to api.playit.gg and api.cloudflare.com.

<div align="center">
<br/>
</div>
