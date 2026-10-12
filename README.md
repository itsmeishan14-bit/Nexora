# Nexora

Nexora is a premium, high-end personal operating system designed to elevate your productivity through "Quiet Intelligence." It combines sophisticated strategic goal tracking with deterministic local AI to help you focus on what matters.

## ✨ Features

- **Strategic Dashboards**: A "Quiet Intelligence" aesthetic using a custom Sage Green design system.
- **AI-Driven Planning**: Local AI orchestration that analyzes your workload, suggests daily plans, and decomposes complex goals into actionable tasks.
- **Productivity Heatmap**: A GitHub-style 15-week contribution grid to visualize your consistency and momentum.
- **Proactive Insights**: Intelligent alerts for workload overload, neglected goals, and task breakdown suggestions.
- **Strategic Goal Tracking**: Advanced goal management with integrated health indicators and progress visualization.
- **Premium UX**: Smooth motion primitives, staggered animations, and tactile micro-interactions built with Jetpack Compose.

## 🛠️ Architecture

- **Clean MVVM**: Strict separation between UI (Compose), State Management (ViewModels), and Business Logic.
- **Local Intelligence**: Deterministic AI engine running entirely on-device for maximum privacy and performance.
- **Deterministic Evaluation**: Integrated benchmarking suite for verifying AI recommendation quality.
- **Reliable Storage**: Room-based database with safe migration strategies and production-ready signing configuration.

## 🚀 Getting Started

### Prerequisites

- Android Studio Ladybug (or newer)
- JDK 17+
- Android SDK 34+

### Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/nexora.git
   cd nexora
   ```

2. **Configure Signing**:
   Nexora uses a secure signing configuration. Copy the template and fill in your local details:
   ```bash
   cp keystore.properties.example keystore.properties
   ```
   Edit `keystore.properties` with your actual keystore path and credentials.

3. **Build & Run**:
   Sync the project with Gradle files and run the `:app` module on your device or emulator.

### Build Commands

- **Debug Build**: `./gradlew assembleDebug`
- **Release Build**: `./gradlew assembleRelease` (Requires configured `keystore.properties`)
- **Run Tests**: `./gradlew testDebugUnitTest`

## 🤖 Gemini Cloud AI & Offline Fallback

Nexora features a dual-engine AI architecture:
- **Cloud Intelligence (Online)**: Powered by Google's official Gemini Developer API using the free-tier eligible `gemini-1.5-flash` model for rich conversational intelligence, open-ended discussions, and task breakdown.
- **On-Device Intelligence (Offline)**: Nexora's built-in local heuristic engine (`LocalAiProvider`), running completely on-device with zero dependencies on external servers or Ollama.

### 🔑 Setting up Free Gemini Cloud AI (Local Development)

1. **Get a Free API Key**:
   - Go to [Google AI Studio](https://aistudio.google.com/app/apikey).
   - Sign in with your Google Account and click **Create API Key**.
   - Gemini 1.5 Flash provides a generous free tier (15 requests/min, 1,500 requests/day, 1M tokens/min) with no credit card required.

2. **Configure in `local.properties`**:
   Add your key to `local.properties` in the project root (this file is excluded from git):
   ```properties
   GEMINI_API_KEY=AIzaSy...your_actual_key_here
   ```
   *Alternative:* Set the environment variable `GEMINI_API_KEY` in your development environment or CI runner.

3. **Compile & Run**:
   - Run `./gradlew assembleDebug`.
   - The status indicator in the **Intelligence** screen will display a green dot with **Gemini Cloud AI · Online**.

### 🛡️ Security & Zero-Cost Architecture

- **Zero-Cost Guarantee**: Nexora never enables paid billing or switches to paid tiers. If your free Gemini quota is exhausted (HTTP 429 `RESOURCE_EXHAUSTED`), Nexora automatically and transparently falls back to local on-device AI mode without incurring any cost.
- **Offline Resilience**: When internet is disconnected, in airplane mode, or if API calls time out, Nexora transparently falls back to the local on-device pipeline. All local task management, goal tracking, and safe confirmation gates continue to function uninterrupted.
- **Production Deployment Note**: In Android applications, API keys embedded directly within an APK are not secure against extraction. Before releasing Nexora to the public (e.g., Google Play Store), implement a secure backend proxy service where server-side secrets authenticate with Google, and the mobile client authenticates securely against your backend.

## 🛡️ Privacy

When operating offline or unconfigured, all AI logic and task data remain strictly on your device. When Gemini Cloud AI is configured, queries are sent directly to the official Google Gemini API using your provided credentials, maintaining strict separation from third-party intermediaries.

