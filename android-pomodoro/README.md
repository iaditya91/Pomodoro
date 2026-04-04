# Android Pomodoro (Jetpack Compose)

Minimal Pomodoro timer app using Jetpack Compose.

Features:
- Focus -> Review -> Break modes
- Main timer screen with Play/Pause and "Start Review/Break/Focus" actions
- Settings (set minutes for each mode) stored in SharedPreferences

How to open and run:    
1. Open `android-pomodoro` in Android Studio.
2. Let Gradle download dependencies.
3. Run the `app` module on an emulator or device.

Notes:
- This is a small skeleton to get started. Compose and Gradle plugin versions are minimal; open in recent Android Studio (Arctic Fox or later recommended).
- Settings are edited via a dialog and saved to `SharedPreferences`.

Files of interest:
- [app/src/main/java/com/pomodoro/MainActivity.kt](app/src/main/java/com/pomodoro/MainActivity.kt)
- [app/src/main/java/com/pomodoro/ui/MainScreen.kt](app/src/main/java/com/pomodoro/ui/MainScreen.kt)
- [app/src/main/java/com/pomodoro/ui/TimerViewModel.kt](app/src/main/java/com/pomodoro/ui/TimerViewModel.kt)
- [app/src/main/java/com/pomodoro/ui/SettingsScreen.kt](app/src/main/java/com/pomodoro/ui/SettingsScreen.kt)
- 