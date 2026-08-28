# Hermes - Android Autonomous Device Agent & Automation Hub

Hermes is an intelligent Android AI agent and autonomous device automation framework built with Kotlin, Jetpack Compose, Material 3, and Room.

## 🚀 Key Capabilities
- **Autonomous Device Agent**: Executes multi-step device actions, settings configuration, sensor monitoring, and smart notifications.
- **RPA & Screen Automation**: Accessibility service integration for UI node inspection, screen clicking, and simulated gestures.
- **24/7 Background Sentinel Daemon**: Persistent foreground service with battery optimization exemption support.
- **Notification Sentinel**: Intercepts incoming notifications to trigger automated workflows.
- **Global Floating Bubble**: Quick overlay trigger anywhere on your device.
- **Offline & Cloud Multi-LLM Engine**: Configurable with Google Gemini and OpenAI-compatible endpoints.

## 📦 How to Build and Get APK

### Automatic Build via GitHub Actions (Recommended)
This repository includes a pre-configured GitHub Actions workflow (`.github/workflows/build-apk.yml`).
1. Push this repository to GitHub.
2. Go to the **Actions** tab on your GitHub repository.
3. Click the latest workflow run and download the **Hermes-Debug-APK** artifact.

### Manual Local Build
```bash
git clone <your-repo-url>
cd hermes
./gradlew assembleDebug
# Generated APK will be in app/build/outputs/apk/debug/app-debug.apk
```
