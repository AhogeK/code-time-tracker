# Contributing to Code Time Tracker

First off, thank you for considering contributing to Code Time Tracker! 👋

This is an open-source project maintained in my spare time. I don't have strict, corporate-style rules—my goal is simply
to build a useful tool and learn together. Whether you're fixing a typo, reporting a bug, or adding a cool new feature,
your help is truly appreciated.

## 🛠️ Development Setup

You don't need a complicated setup to get started.

### Prerequisites

- **JDK 21** (Required for building the project)
- **IntelliJ IDEA** (Community or Ultimate, version 2025.1 or later is recommended)

### How to Run Locally

1. **Fork** and clone the repository.
2. Open the project in IntelliJ IDEA.
    - The IDE should automatically recognize the Gradle project.
    - Wait for indexing and dependency download to finish.
3. **Run the Plugin**:
    - Open the Gradle tool window on the right.
    - Navigate to `Tasks` -> `intellij platform` -> `runIde`.
    - A new IDE instance (Sandbox) will launch with the plugin pre-installed.

## 🐛 Reporting Bugs

Found something broken? Please open an [Issue](https://github.com/AhogeK/code-time-tracker/issues) and let me know:

- **What happened**: A brief description of the bug.
- **Environment**: Your IDE version (e.g., IntelliJ IDEA 2024.3) and OS.
- **Steps to reproduce**: How can I see the bug myself?
- **Screenshots/Logs**: If applicable, these are super helpful!

## 💡 Feature Requests

Have an idea for a new chart or metric? Feel free to open an Issue to discuss it!

- Explain **what** you want to achieve.
- Explain **why** it would be useful.

## 💻 Pull Requests (PRs)

I welcome Pull Requests of all sizes!

1. **Create a branch**: Give it a simple name like `fix-npe` or `feature-dark-mode`.
2. **Make your changes**:
    - Try to follow the existing Kotlin coding style (IntelliJ's default formatting is fine).
    - If you're adding complex logic, adding a unit test would be awesome (but not strictly mandatory for small UI
      tweaks).
3. **Test your changes**:
    - Run `./gradlew test` to ensure nothing broke.
    - Run the plugin locally (`runIde`) to verify it looks good.
4. **Submit**: Push to your fork and open a PR. I'll review it as soon as I can!

## 🤝 Code of Conduct

Please be kind and respectful to everyone in the community. We are here to help each other and build something cool.

---
**Thank you for your support!** 🚀