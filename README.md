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
- **Run Tests**: `./gradlew test`

## 🛡️ Privacy

All AI logic and task data remain strictly on your device. Nexora is designed to be your private strategic partner, ensuring your productivity data never leaves your control.
