# Hermes - Android Autonomous Device Agent & Automation Hub

[![Release](https://img.shields.io/badge/version-v1.2.0-blue.svg)](https://github.com/)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen.svg)](https://developer.android.com)

**Hermes** is a next-generation Android AI Agent & autonomous device automation hub powered by Google Gemini and advanced on-device RPA capabilities, built with **Kotlin**, **Jetpack Compose**, **Material 3 (M3)**, and **Room Database**.

---

## 🌟 What's New in v1.2.0

- 🌊 **Real-Time Streaming Output**: Instant dynamic typewriter animation with typing cursor feedback.
- 🧠 **Collapsible Deep Thinking (CoT)**: Organized chain-of-thought logic cards that can be smoothly expanded or collapsed.
- 📦 **Compact Action Pipeline Monitor**: Ultra-compact status bar for active execution steps with tap-to-expand timeline views.
- 🎙️ **TTS & Voice Waveform Visualizer**: Text-to-Speech voice broadcast and dynamic audio level visualization for mic inputs.
- ⚡ **Dynamic Context-Aware Suggestions**: Real-time suggestion chips generated from battery telemetry, clipboard state, and time of day.
- 🛡️ **Offline Heuristic Resilience**: Seamless local fallback to heuristic neural rules when cloud LLM endpoints encounter network/API errors.
- 📋 **Message Action Bar**: Fast one-tap copy, text-to-speech recitation, and prompt regeneration.

---

## 🚀 Key Capabilities

- **Autonomous Device Agent**: Multi-step goal resolution, device parameter tuning, sensor diagnostics, and toast/notification dispatching.
- **RPA & Screen Automation**: Accessibility service integration for UI node hierarchy inspection, screen clicking, and simulated gestures.
- **24/7 Sentinel Daemon**: Persistent foreground service with battery optimization exemption support for nonstop task loops.
- **Multi-LLM Engine**: Google Gemini API, OpenAI-compatible custom endpoints, DeepSeek, Claude, and local heuristic engines.
- **Global Floating Bubble**: Quick overlay trigger anywhere on your device.
- **Custom Scripts & Plugins**: JavaScript/Lua automation scripts and modular plugin ecosystem.

---

## 📦 How to Publish & Release to GitHub

### Method 1: Push via AI Studio / Git with Tag (Recommended)

1. Export the project or push directly to your GitHub repository.
2. In your local terminal or GitHub web interface, create and push a version tag:
   ```bash
   git tag -a v1.2.0 -m "Hermes v1.2.0 Release: Real-time Streaming & Collapsible CoT"
   git push origin v1.2.0
   ```
3. GitHub Actions (`.github/workflows/build-apk.yml`) will automatically compile the APK and publish a **GitHub Release** with the APK asset attached!

### Method 2: Manual Local Compilation

```bash
git clone <your-repo-url>
cd hermes
./gradlew assembleDebug
# Generated APK: app/build/outputs/apk/debug/app-debug.apk
```
