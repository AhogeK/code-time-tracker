# Code Time Tracker

[![Version](https://img.shields.io/badge/version-0.19.5-blue.svg)](https://github.com/AhogeK/code-time-tracker)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-IntelliJ%202026.1%2B-orange.svg)](https://plugins.jetbrains.com/)

A JetBrains Platform plugin for automatic coding time tracking and analytics.

## 📋 Requirements

- **JetBrains IDEs 2026.1** or later (Build 261+)
  * Compatible with IntelliJ IDEA, PyCharm, WebStorm, GoLand, Android Studio, etc.
- **Java 25** runtime (Bundled with IDE 2026.1+)
- **ctt-server** (optional) - Self-hosted sync backend for cloud sync; not required for local-only tracking

## 📸 Gallery

<details>
  <summary><b>Timer Display</b></summary>
  <br>
  <p>The bottom toolbar displays a timer, which you can click to select the type you want to show.</p>
  <img src="assets/Screenshot%202025-12-14%20at%2016.31.05.png" alt="Timer Display" width="600">
</details>

<details>
  <summary><b>Dashboard & Statistics</b></summary>
  <br>
  <p>If the chart is not in the right-side toolbar, you can find it under the menu bar: View → Appearance.</p>
  <p>Clicking it will open the statistics chart content for the coding time.</p>
  <img src="assets/Screenshot%202025-12-14%20at%2016.35.32.png" alt="Dashboard & Statistics menu" width="600">
  <img src="assets/Screenshot%202025-12-14%20at%2016.43.54.png" alt="Dashboard & Statistics" width="600">
</details>

<details>
  <summary><b>Import/Export Data</b></summary>
  <br>
  <p>The project includes import and export functions to facilitate data synchronization between different devices. It will help you skip duplicate data.</p>
  <img src="assets/Screenshot%202025-12-14%20at%2018.29.29.png" alt="Import/Export Data" width="400">
  <img src="assets/Screenshot%202025-12-14%20at%2018.30.45.png" alt="Import/Export Data" width="400">
</details>

## ✨ Features

- **🎯 Automatic Tracking** - Captures keyboard and mouse activity with idle detection
- **📊 Real-time Analytics** - Live status bar widget with period-based statistics
- **🗂️ Multi-Project Support** - Track multiple projects independently
- **📈 Visual Insights** - Heatmaps, language distribution, hourly patterns
- **💾 Data Management** - Export/import sessions in JSON format
- **☁️ Cloud Sync** - Optional multi-device session sync via your own self-hosted [ctt-server](https://github.com/AhogeK/ctt-server):
  1.  Run your own ctt-server instance (follow setup instructions in the server repository)
  2.  Create a sync API key in your server admin interface
  3.  Paste the API key into the plugin **Settings → Sync → API Key**
  4.  Click **Bind** — the plugin automatically registers your device with the server and keeps sessions synchronized automatically
  5.  Sync runs periodically after IDE startup (interval configurable in Settings); click **Sync Now** to trigger a manual sync
- **Conflict resolution**: Last-write-wins based on modification timestamp; concurrent changes to the same session leave the local edit untouched and the server resolves it on the next push, so no data is ever lost.
- **🔒 Privacy First** - All data stored locally in SQLite; cloud sync is opt-in and only communicates with your own self-hosted backend.

## ☁️ Cloud Sync Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| "Cannot reach the server" on bind or sync | Server address wrong, server down, or network unreachable | Verify the server is running and the address in **Settings → Sync** starts with `http://` or `https://` |
| "The API key is invalid" | Key mistyped or deleted | Re-enter the key and bind again; create a new key in the server admin if it was removed |
| "The API key has been revoked" | Key disabled in the server admin | Create a new API key and bind it |
| "The API key lacks the required scope" | Key created without the SYNC scope | Recreate the key with the SYNC scope enabled |
| Sync skips a round silently | Temporary network issue or rate limit | Sync retries automatically on the next interval; click **Sync Now** to trigger immediately |
| Statistics only show one account's data after binding | Account-scoped isolation | By design: bound data is isolated per account. Unbind to view the full local dataset |
| Sessions never appear on the other device | Key not bound on the second device | Bind the same API key on every device you want to keep in sync |

<details>
  <summary><b>How sync resolves conflicts</b></summary>
  <br>
  <p>Concurrent edits to the same session are resolved server-side by last-write-wins
  (LWW): the session state with the higher version or the later modification time wins,
  and a delete always beats a live edit. Every device pulls the server-authoritative
  state after each push, so all devices converge on a single row — no data is lost.</p>
</details>

## 🤝 Contributing

Contributions are always welcome!

Please read our [Contributing Guide](CONTRIBUTING.md) to learn how to set up the development environment and submit Pull Requests.

Please also note that this project is released with a [Code of Conduct](CODE_OF_CONDUCT.md). By participating in this project you agree to abide by its terms.

## 📄 License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

## 📬 Contact

**Maintainer**: AhogeK  
**GitHub**: [@AhogeK](https://github.com/AhogeK)  
**Website**: [ahogek.com](https://www.ahogek.com)

<div align="center">

## 💖 Support This Project

[![Ko-fi](https://img.shields.io/badge/Ko--fi-FF5E5B?style=plastic&logo=ko-fi&logoColor=white)](https://ko-fi.com/ahogek)
&nbsp;&nbsp;
[![Afdian](https://img.shields.io/badge/爱发电-946ce6?style=plastic&logo=github-sponsors&logoColor=white)](https://afdian.net/a/AhogeK)
&nbsp;&nbsp;
[![Solana](https://img.shields.io/badge/Solana-14F195?style=plastic&logo=solana&logoColor=white)](https://solscan.io/account/55XnqvGKwH6LamJB7tSwUbrmJikEU2zwP3k1FjsdyEys)

<p align="center">
Made with ❤️ for developers who value your time
</p>
