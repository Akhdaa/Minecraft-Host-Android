<div align="center">

<br/>

<img src="docs/logo.png" alt="MC Host" width="160" />

# MC Host

### 直接在 Android 手机上运行的 Minecraft 服务器

[English](README.md) · [Bahasa Indonesia](README.id.md) · **中文** · [Русский](README.ru.md)

<br/>

[![Android](https://img.shields.io/badge/Android-8.0%2B%20arm64-3fa34a?style=for-the-badge&logo=android&logoColor=white&labelColor=0d1117)](#download)
[![Minecraft](https://img.shields.io/badge/Paper%20·%20Fabric%20·%20Forge%20·%20NeoForge-1.17%2B-5fd068?style=for-the-badge&labelColor=0d1117)](#features)
[![Java](https://img.shields.io/badge/Java-17%20·%2021%20·%2025%20bundled-e8efe9?style=for-the-badge&logo=openjdk&logoColor=white&labelColor=0d1117)](#features)
[![License](https://img.shields.io/badge/License-GPLv3-93a597?style=for-the-badge&labelColor=0d1117)](LICENSE)

<br/>

[**下载**](#download) · [**功能**](#features) · [**快速开始**](#start) · [**通过互联网游玩**](#tunnel) · [**常见问题**](#faq) · [**免责声明**](#disclaimer) · [**许可证**](#license)

</div>

> [!IMPORTANT]
> MC Host 与 Mojang、Microsoft、PaperMC、FabricMC、Forge、NeoForged、Modrinth、playit.gg 和 Cloudflare 均无关联。运行服务器即表示你同意 [Minecraft EULA](https://aka.ms/MinecraftEULA)。

---

<div align="center">

<img src="docs/banner.png" alt="MC Host banner" width="100%" />

<h1><a id="features"></a>功能</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### 服务器
- **一个 APK，无需 Termux。** APK 内置 Android 版 OpenJDK 17、21 和 25，会根据 Minecraft 版本自动选择。
- **多个服务器。** 模组版 1.20.1 世界和 Paper 1.21 生存服可以并存，一键切换（同一时间运行一个）。
- **六种服务器类型：** Paper、Purpur、Fabric、Forge、NeoForge 和 Vanilla，支持 Minecraft **1.17 到最新版**（NeoForge 从 1.20.2 起）。
- **创建向导：** 选择软件、版本、名称和内存，同意 EULA，完成。
- **启动、停止、重启、强制停止**，崩溃后自动重启。
- **无人时休眠（可选）：** 没有玩家 N 分钟后服务器停止，有人加入时自动唤醒，地址保持不变。
- **每日重启（可选）：** 在你选择的时间重启，游戏内会倒计时提醒。
- **从 Modrinth 整合包（.mrpack）创建：** 一次性配置加载器、服务器端模组和配置。
- **后台运行**：前台服务、唤醒锁和 Wi-Fi 锁，可在打开应用或手机开机时自动启动。

#### 模组和世界
- **应用内 Modrinth 商店**：适配服务器版本的模组、插件和数据包，支持排序、分类，自动安装依赖。
- **Fabric 手机优化包**一键安装：Lithium、FerriteCore、Krypton、ModernFix、Chunky。
- **管理模组/插件**：搜索、启用、停用、删除、上传 `.jar`，并显示来自 Modrinth 的真实名称和图标。
- **一键更新模组：** 查看哪些模组有适配你服务器的新版本，单个或全部更新。
- **备份和恢复**（带进度）、服务器运行时自动备份、从电脑上传世界、下载备份。
- **世界文件**：浏览服务器文件夹，查看主世界/下界/末地大小，下载、上传、重命名、删除。
- **配置编辑器**，支持 `.toml`、`.json`、`.properties` 等文件，还有带说明的 `server.properties` 表单。
- **只重置下界或末地**，不影响主世界。
- **完整服务器备份（可选）：** 世界加模组、插件和配置；可整体恢复、变成新服务器，或导入打包的服务器文件夹。

    </td>
    <td width="50%" valign="top">

#### 面板
- **网页面板**在 `8080` 端口，可在应用内（WebView）或同一 Wi-Fi 下的任何浏览器中打开。
- **英语、印尼语、中文和俄语**，首次打开有每个页面的导览。
- **实时控制台**：日志级别筛选、搜索、命令历史、快捷命令和难度按钮。
- **玩家管理**：OP、白名单、游戏模式、治疗、踢出、封禁、解封。
- **手机状态**：服务器内存、运行时间、电量和温度。
- **通知（可选）：** 服务器就绪、崩溃、休眠或唤醒，玩家加入和离开，手机过热。
- **PIN 登录**（其他设备），输错 3 次会暂时锁定。
- **配置备份**：保存应用设置（PIN、playit 密钥、Cloudflare 域名和令牌），重装后或在新手机上恢复。
- **手机和电脑屏幕上都显示良好。**

#### 通过互联网游玩
- **内置 playit.gg 隧道**（为 Android 编译的 playitd），免费。
- **基岩版玩家（可选）：** 一个开关即可安装适配服务器版本的 Geyser + Floodgate，手机和主机玩家也能加入。
- **地址显示在服务器页**：复制后发给好友。
- **通过 Cloudflare 使用自己的域名**：自动计算并检查 SRV 记录，也可用 API 令牌自动创建。
- 隧道页中有 playit 和 Cloudflare 的**分步教程**。

    </td>
  </tr>
</table>

<table>
  <tr>
    <td align="center"><img src="docs/screens/home.png" width="200" /><br/><sub>服务器</sub></td>
    <td align="center"><img src="docs/screens/console.png" width="200" /><br/><sub>控制台和玩家</sub></td>
    <td align="center"><img src="docs/screens/store.png" width="200" /><br/><sub>Modrinth 商店</sub></td>
    <td align="center"><img src="docs/screens/domain.png" width="200" /><br/><sub>Cloudflare 域名</sub></td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="download"></a>下载</h1>

从 [**Releases**](../../releases/latest) 下载最新的 `MCHost-1.0.x.apk`。每次推送到 `main` 都会由 GitHub Actions 构建新的 APK。

</div>

**在手机上安装：**打开 APK 并允许“安装未知应用”。Android 会提示应用是为旧版本构建的，这是正常的（见[常见问题](#faq)）。

**用 ADB 安装：**在电脑上运行 [`install-mchost.bat`](install-mchost.bat)，手机需开启 USB 调试并连接。脚本会下载最新 APK、安装或更新、关闭电池优化并打开应用。也可以手动执行：

```bat
adb install -r --bypass-low-target-sdk-block MCHost-1.0.x.apk
adb shell dumpsys deviceidle whitelist +com.minecrafthost.id
```

更新总是安全的：所有版本都使用同一个密钥签名，世界、模组和设置都会保留。

**在 Windows 上安装：** 在同一个 [Releases](../../releases/latest) 页面下载 `MCHost-1.0.x-win.msi` 并运行。它只为当前用户安装，不需要管理员权限。不想安装？把 `MCHost-1.0.x-win-portable.zip` 解压到任意位置，运行 `MC Host.exe` 即可。

- 需要 64 位 Windows 10 或 11。面板在独立窗口中打开（使用 WebView2，Windows 11 和较新的 Windows 10 已自带）。
- 关闭窗口不会停止服务器，MC Host 会留在托盘继续运行。右键托盘图标可退出，退出前会先正常关闭服务器。
- 服务器第一次需要 Java 时会自动下载，不用另外安装。数据保存在 `%LOCALAPPDATA%\MCHost`。
- 因为安装包还没有代码签名，Windows SmartScreen 可能会提示：点 **More info**，再点 **Run anyway**。
- 服务器第一次启动时，Windows 防火墙会询问 `java.exe`。请允许 **专用网络**，同一 Wi-Fi 的好友才能加入。
- 在手机和电脑之间迁移：先在设置里开启完整服务器备份，然后在另一台设备上用 **导入服务器**。
- 把 `.jar` 拖到面板上可安装为模组或插件，拖入 `.zip` 可导入服务器或恢复世界备份。

---

<div align="center">

<h1><a id="start"></a>快速开始</h1>

</div>

1. 打开 **MC Host**，允许通知，选择语言。简短的介绍和导览会带你熟悉各个页面。
2. 点击 **+** 创建服务器：选择软件（插件选 Paper，模组选 Fabric/Forge/NeoForge）、Minecraft 版本、名称和内存（8 GB 手机：3 到 4 GB），并同意 EULA。软件会立即开始下载。
3. Fabric 服务器？打开 **模组 > Modrinth 商店**，在手机优化包上点击 **全部安装**。
4. 在 **服务器** 页点击 **启动**。首次启动需要生成世界，所以更久。
5. 同一 Wi-Fi 的好友使用服务器页上显示的 **LAN** 地址加入。
6. 在 **设置** 中点击 **关闭电池优化**，然后在应用信息中开启 **自启动**（MIUI/HyperOS），防止 Android 杀掉服务器。

要在电脑上打开面板，在浏览器中输入 **PANEL** 地址（例如 `http://192.168.1.36:8080`），再输入 **设置 > 面板访问** 中的 PIN。

---

<div align="center">

<h1><a id="tunnel"></a>通过互联网游玩</h1>

</div>

完整的图文教程在应用的 **隧道 > 帮助** 中。简要步骤：

| 步骤 | playit.gg（必需，免费） | Cloudflare（可选） |
|---|---|---|
| 1 | 在 [playit.gg](https://playit.gg) 注册，添加 **Docker** 类型的 agent，复制它的 `SECRET_KEY` | 你的域名已在 Cloudflare 上生效 |
| 2 | 粘贴到 **隧道** 页，**保存并连接**，**启动隧道** | 用 **Edit zone DNS** 模板创建 API 令牌，并添加 **Zone · Read** |
| 3 | 在 playit 控制台创建指向 `127.0.0.1:25565` 的 **Minecraft Java** 隧道 | 在 **Cloudflare 域名** 卡片中填写域名和令牌 |
| 4 | 地址会出现在 **服务器** 页，点击复制 | 点击 **立即更新记录**，然后 **检查 DNS** |

之后好友直接加入 `xxx.joinmc.link` 或 `mc.yourdomain.com`。在控制台页开启 **白名单**，只让你的好友进入。

---

<div align="center">

<h1><a id="faq"></a>常见问题</h1>

</div>

<details>
<summary><b>为什么 Android 提示这个应用是为旧版本构建的？</b></summary>

`targetSdk` 故意设为 28。只有目标 API 为 28 或更低的应用，Android 才允许它运行自己数据文件夹里的程序（这里是 `java` 和 `playitd`）；Termux 也是这样做的。在 Android 14 及以上，如果安装被拦截，请使用 `adb install --bypass-low-target-sdk-block`。
</details>

<details>
<summary><b>需要 root 吗？</b></summary>

不需要。所有功能都无需 root，已 root 的手机（Magisk / KernelSU）也能正常使用，无需授予 root 权限。root 只对应用以外的事情有帮助，比如防止内存不足时 Android 杀掉服务器进程，或调整 CPU 调度器。
</details>

<details>
<summary><b>使用非正版 Minecraft 的好友能加入吗？</b></summary>

可以。在 **世界 > 设置** 中关闭 **正版验证（Online mode）**，然后开启 **白名单** 并在控制台页添加好友。没有白名单的话，任何人都能冒用别人的名字加入。
</details>

<details>
<summary><b>只是跑步，日志却显示 "moved too quickly"</b></summary>

这是原版 Minecraft 的移动检查，不是 bug。当连接（例如通过 playit 隧道）卡顿一两秒时，你的移动数据会一次性到达，看起来像一下跳了 10 格以上，于是服务器把你稍微拉回。使用 LAN 地址游玩可以避免。如果日志里同时出现 "Can't keep up!"，说明是手机本身负载过高。
</details>

<details>
<summary><b>服务器卡顿或手机发烫？</b></summary>

安装手机优化包，把*视距*降到 5 或 6，用 `chunky radius 1000` 再 `chunky start` 预生成世界，并使用 Wi-Fi 而不是移动数据。模组很多的服务器，尽量不要在同一台手机上同时开服和游玩。
</details>

---

<div align="center">

<h1><a id="disclaimer"></a>免责声明</h1>

</div>

MC Host 是一个业余项目：利用空闲时间出于兴趣制作，用一台闲置的手机给朋友开 Minecraft 服务器。它不是商业托管服务，也没有人因维护它而获得报酬。功能可能变化，版本之间可能出问题，不提供任何形式的保证或在线时间承诺。

MC Host 仅供合法的个人使用：和朋友一起玩、学习服务器的工作方式，以及在自己的设备上做实验。服务器由你运行，谁能加入、服务器里发生什么都由你决定。请保管好面板 PIN，使用白名单，备份重要的世界，并留意手机温度。

本声明适用于 MC Host 的所有版本、构建、分支和分发渠道；每个副本都保留其适用的许可证。

---

<div align="center">

<h1><a id="license"></a>许可证</h1>

</div>

**GPL-3.0-or-later**

MC Host 自身的代码采用 [GNU GPL v3](LICENSE) 或更高版本授权。第三方内容保留其各自的许可证和版权声明；APK 内置 OpenJDK（GPLv2 附 Classpath Exception）和 playitd（BSD 2-Clause），完整文本见 [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt)。

运行服务器需要拥有 Minecraft 并同意 [Minecraft EULA](https://aka.ms/MinecraftEULA)。MC Host 不捆绑 Minecraft：Vanilla、Paper、Purpur、Fabric、Forge 和 NeoForge 都在手机上从官方来源下载，并遵循各自的许可证；来自 Modrinth 的模组和插件遵循其作者的许可证。MC Host 不授予任何 Minecraft 许可、账号或权利。不允许使用盗版、被盗账号，或绕过 Mojang 及其他任何方的许可条款。本说明不修改 GPL 或第三方许可证。

playit 密钥和 Cloudflare 令牌保存在应用的私有存储中，只会发送到 api.playit.gg 和 api.cloudflare.com。

<div align="center">
<br/>
</div>
