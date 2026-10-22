<div align="center">

<br/>

<img src="docs/logo.png" alt="MC Host" width="160" />

# MC Host

### A Minecraft server running right on your Android phone

**English** · [Bahasa Indonesia](README.id.md) · [中文](README.zh.md) · [Русский](README.ru.md)

<br/>

[![Android](https://img.shields.io/badge/Android-8.0%2B%20arm64-3fa34a?style=for-the-badge&logo=android&logoColor=white&labelColor=0d1117)](#download)
[![Minecraft](https://img.shields.io/badge/Paper%20·%20Fabric%20·%20Forge%20·%20NeoForge-1.17%2B-5fd068?style=for-the-badge&labelColor=0d1117)](#features)
[![Java](https://img.shields.io/badge/Java-17%20·%2021%20·%2025%20bundled-e8efe9?style=for-the-badge&logo=openjdk&logoColor=white&labelColor=0d1117)](#features)
[![License](https://img.shields.io/badge/License-GPLv3-93a597?style=for-the-badge&labelColor=0d1117)](LICENSE)

<br/>

[**Download**](#download) · [**Features**](#features) · [**Getting started**](#start) · [**Play over the internet**](#tunnel) · [**FAQ**](#faq) · [**Disclaimer**](#disclaimer) · [**License**](#license)

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
- **Sleep when empty (optional):** the server stops after N minutes without players and wakes up by itself when someone joins; the address stays the same.
- **Daily restart (optional)** at a time you pick, with an in-game countdown.
- **Create from a Modrinth modpack (.mrpack):** loader, server-side mods and config set up in one go.
- **Runs in the background** with a foreground service, wake lock and Wi-Fi lock, and can start when the app opens or the phone boots.

#### Mods & World
- **Modrinth store** in the app: mods, plugins and datapacks for your server's version, sorting, categories, dependencies installed automatically.
- **Phone optimization pack** for Fabric in one tap: Lithium, FerriteCore, Krypton, ModernFix, Chunky.
- **Manage mods/plugins:** search, enable, disable, delete, upload `.jar`, with real names and icons from Modrinth.
- **Update mods in one tap:** see which mods have a newer version for your server and update one or all.
- **Backup & restore** with progress, automatic backups while the server runs, upload a world from your PC, download backups.
- **World files:** browse the server folder, see Overworld/Nether/End sizes, download, upload, rename, delete.
- **Config editor** for `.toml`, `.json`, `.properties` and similar files, plus a guided `server.properties` form.
- **Reset only the Nether or the End** without touching the Overworld.
- **Full server backups (optional):** world plus mods, plugins and config; restore them whole, turn one into a new server, or import a zipped server folder.

    </td>
    <td width="50%" valign="top">

#### Panel
- **Web panel** on port `8080`, inside the app (WebView) or in any browser on the same Wi-Fi.
- **English, Indonesian, Chinese and Russian**, with a first-run tour of every screen.
- **Live console** with level filters, search, command history, quick commands and difficulty buttons.
- **Player management:** OP, whitelist, gamemode, heal, kick, ban, unban.
- **Phone stats:** server RAM, uptime, battery and temperature.
- **Notifications (optional):** server ready, crashed, asleep or woken up, players joining and leaving, phone too hot.
- **PIN login** for other devices; 3 wrong tries locks login for a while.
- **Config backup:** save the app settings (PIN, playit secret, Cloudflare domain and token) and restore them after a reinstall or on a new phone.
- **Looks right** on phones and on desktop screens.

#### Play over the internet
- **Built-in playit.gg tunnel** (playitd built for Android), free.
- **Bedrock players (optional):** one switch installs Geyser + Floodgate for your server version so phone and console players can join.
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
adb shell dumpsys deviceidle whitelist +com.minecrafthost.id
```

Updates are always safe: worlds, mods and settings are kept because every build is signed with the same key.

**Install on Windows:** download `MCHost-1.0.x.msi` from the same [Releases](../../releases/latest) page and run it. It installs for your user only, so no admin rights are needed. Prefer no installer? Unzip `MCHost-1.0.x-portable.zip` anywhere and run `MC Host.exe`.

- Windows 10 or 11, 64-bit. The panel opens in its own window (it uses WebView2, which ships with Windows 11 and current Windows 10).
- Closing the window keeps the server running in the tray. Right-click the tray icon to quit; MC Host stops the server cleanly first.
- Java is downloaded the first time a server needs it, so you don't install anything else. Your data lives in `%LOCALAPPDATA%\MCHost`.
- Windows SmartScreen may warn about the installer because it isn't code-signed yet: click **More info**, then **Run anyway**.
- The first time a server starts, Windows Firewall asks about `java.exe`. Allow **Private networks** so friends on your Wi-Fi can join.
- Moving between phone and PC: turn on the full server backup in Settings, then use **Import server** on the other device.
- Drag a `.jar` onto the panel to install it as a mod or plugin, or a `.zip` to import a server or restore a world backup.

---

<div align="center">

<h1><a id="start"></a>Getting started</h1>

</div>

1. Open **MC Host**, allow notifications, pick your language. The short intro and tour show you around.
2. Tap **+** to create a server: choose the software (Paper for plugins, Fabric/Forge/NeoForge for mods), the Minecraft version, a name and RAM (8 GB phone: 3 to 4 GB), and accept the EULA. The software downloads right away.
3. Fabric server? Open **Mod > Modrinth store** and tap **Install all** on the phone optimization pack.
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
| 4 | The address appears on the **Server** tab, tap copy | **Update record now**, then **Check DNS** |

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

<h1><a id="disclaimer"></a>Disclaimer</h1>

</div>

MC Host is a hobby project: built for fun, in spare time, to run a Minecraft server for friends on a phone that would otherwise sit in a drawer. It is not a commercial hosting service and nobody is paid to support it. Features come and go, things can break between versions, and there is no warranty or uptime guarantee of any kind.

MC Host is for lawful personal use: playing with friends, learning how servers work, and experimenting on hardware you own. You run the server, so you decide who joins and what happens on it. Keep your panel PIN private, use the whitelist, keep backups of worlds you care about, and keep an eye on the phone's temperature.

This position covers every MC Host version, build, fork and distribution channel; each copy keeps its applicable licenses.

---

<div align="center">

<h1><a id="license"></a>License</h1>

</div>

**GPL-3.0-or-later**

MC Host's own code is licensed under the [GNU GPL v3](LICENSE) or later. Third-party material keeps its own licenses and copyright notices; the APK bundles OpenJDK (GPLv2 with Classpath Exception) and playitd (BSD 2-Clause), see [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt) for the full texts.

You must own Minecraft and accept the [Minecraft EULA](https://aka.ms/MinecraftEULA) to run a server. MC Host does not bundle Minecraft: Vanilla, Paper, Purpur, Fabric, Forge and NeoForge are downloaded on the phone from their official sources and stay under their own licenses, and mods or plugins from Modrinth follow their authors' licenses. MC Host grants no Minecraft license, account or entitlement. Pirated copies, stolen accounts, and bypassing Mojang's or anyone else's license terms are not authorized. This guidance does not amend the GPL or third-party licenses.

The playit secret and Cloudflare token are kept in the app's private storage and only sent to api.playit.gg and api.cloudflare.com.

<div align="center">
<br/>
</div>
