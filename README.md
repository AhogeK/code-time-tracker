# Code Time Tracker

[![Version](https://img.shields.io/badge/version-0.6.2--SNAPSHOT-blue.svg)](https://github.com/AhogeK/code-time-tracker)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-IntelliJ%202025.2%2B-orange.svg)](https://plugins.jetbrains.com/)

A professional IntelliJ Platform plugin for automatic coding time tracking and analytics.

## Features

- **🎯 Automatic Tracking** - Captures keyboard and mouse activity with idle detection
- **📊 Real-time Analytics** - Live status bar widget with period-based statistics
- **🗂️ Multi-Project Support** - Track multiple projects independently
- **📈 Visual Insights** - Heatmaps, language distribution, hourly patterns
- **💾 Data Management** - Export/import sessions in JSON format
- **🔒 Privacy First** - All data stored locally in SQLite

## Quick Start

### Installation

```bash
# Build from source
git clone https://github.com/AhogeK/code-time-tracker.git
cd code-time-tracker
./gradlew buildPlugin
```

# Install: build/distributions/code-time-tracker-*.zip

### Usage

1. **Status Bar Widget** - Click to switch between Today/Week/Month/Year/Total
2. **Statistics View** - Open via `View → Tool Windows → Code Statistics`
3. **Export Data** - Right-click widget → Export to JSON

## Architecture

```text
┌─────────────────────────────────────┐
│  Widget / ToolWindow (UI Layer)     │
├─────────────────────────────────────┤
│  TimeTrackerService (Core Logic)    │
│  - Activity Detection               │
│  - Session Management               │
│  - Idle Timeout (60s)               │
├─────────────────────────────────────┤
│  DatabaseManager (Persistence)      │
│  - SQLite with Optimized Indexes    │
│  - Adaptive Query Strategy          │
│  - Batch Operations                 │
└─────────────────────────────────────┘
```

### Database Schema

```sql
CREATE TABLE IF NOT EXISTS coding_sessions
(
    -- Primary Key. A simple auto-incrementing integer for local use.
    id            INTEGER PRIMARY KEY AUTOINCREMENT,

    -- Globally Unique ID. A UUID for the session record itself. Critical for merging and syncing.
    session_uuid  TEXT    NOT NULL UNIQUE,

    -- The unique ID of the user/installation. Prevents data contamination between different users.
    user_id       TEXT    NOT NULL,

    -- The display name of the project.
    project_name  TEXT    NOT NULL,

    -- The programming language used.
    language      TEXT    NOT NULL,

    -- The user's operating system (e.g., "macOS Sonoma").
    platform      TEXT    NOT NULL,

    -- The JetBrains IDE product being used (e.g., "IntelliJ IDEA", "PyCharm").
    ide_name      TEXT    NOT NULL,

    -- The session's start time, in ISO-8601 format.
    start_time    TEXT    NOT NULL,

    -- The session's end time, in ISO-8601 format.
    end_time      TEXT    NOT NULL,

    -- Timestamp for syncing. Used for conflict resolution during merges.
    last_modified TEXT    NOT NULL,

    -- Soft Delete Flag. A boolean (0 or 1) for marking records as deleted.
    is_deleted    INTEGER NOT NULL DEFAULT 0,

    -- Cloud Sync State Flag. 0 = not synced, 1 = synced to cloud.
    -- Used for incremental sync to identify local changes that need uploading.
    is_synced     INTEGER NOT NULL DEFAULT 0,

    -- Cloud Sync Timestamp. Records when this session was last successfully synced.
    -- NULL if never synced. Used for debugging sync issues and calculating sync lag.
    synced_at     TEXT,

    -- Sync Version. Incremented on each modification for optimistic locking.
    -- Prevents lost updates when syncing concurrent changes from multiple devices.
    -- Example: Device A and B both modify same session; higher version wins.
    sync_version  INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_session_uuid
    ON coding_sessions (session_uuid);

CREATE INDEX IF NOT EXISTS idx_sessions_time_range
    ON coding_sessions (is_deleted, start_time, end_time);

CREATE INDEX IF NOT EXISTS idx_sessions_min_time
    ON coding_sessions (is_deleted, start_time);

CREATE INDEX IF NOT EXISTS idx_sessions_sync_state
    ON coding_sessions (is_synced, last_modified);
```

## Technical Stack

| Component         | Version  | Purpose          |
|-------------------|----------|------------------|
| Kotlin            | 2.2.21   | Primary language |
| Java              | 21       | Target platform  |
| Gradle            | 9.2.1    | Build tool       |
| SQLite            | 3.51.1.0 | Local database   |
| IntelliJ Platform | 2025.2+  | Plugin SDK       |

## Development

### Prerequisites

- JDK 21+
- IntelliJ IDEA 2025.2+
- Gradle 9.2+

### Setup

```bash
# Run in sandbox IDE
./gradlew runIde

# Run tests
./gradlew test

# Check for updates
./gradlew dependencyUpdates
```

### Project Structure

```text
src/main/kotlin/com/ahogek/codetimetracker/
├── action/           # Actions
├── activity/         # Activity detection
├── database/         # Data access layer
├── handler/          # Event handlers
├── listeners/        # Event listeners
├── model/            # Data models
├── service/          # Core business logic
├── statistics/       # Analytics engine
├── toolwindow/       # Tool windows
├── topics/           # Topics
├── ui/               # Dialogs & forms
├── user/             # User manager configuration
├── util/             # Utilities
└── widget/           # Status bar UI
```

## Contributing

Contributions welcome! Please follow:

1. Fork & create feature branch
2. Follow [Kotlin conventions](https://kotlinlang.org/docs/coding-conventions.html)
3. Write tests for new features
4. Submit PR with clear description

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

## Contact

**Maintainer**: AhogeK  
**GitHub**: [@AhogeK](https://github.com/AhogeK)  
**Website**: [ahogek.com](https://www.ahogek.com)

<div align="center">

## 💖 Support This Project

<p>
  <a href="https://ko-fi.com/ahogek">
    <img style="border-radius: 8px;" src="https://img.shields.io/badge/Ko--fi-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Ko-fi"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://afdian.com/a/AhogeK">
    <img style="border-radius: 8px;" src="https://img.shields.io/badge/爱发电-946ce6?style=for-the-badge&logo=github-sponsors&logoColor=white" alt="Afdian"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://solscan.io/account/55XnqvGKwH6LamJB7tSwUbrmJikEU2zwP3k1FjsdyEys">
    <img style="border-radius: 8px;" src="https://img.shields.io/badge/Solana-14F195?style=for-the-badge&logo=solana&logoColor=white" alt="Solana"/>
  </a>
</p>

<sub>Your support helps maintain and improve this project 🙏</sub>

</div>

---

<p align="center">
Made with ❤️ for developers who value their time
</p>
