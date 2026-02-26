# Attention Tool

<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="128" height="128" alt="AttentionTool Icon"/>
</div>

A simple Android app , featuring Pomodoro timer and todo list management, for helping you stay focused on your tasks.

## Features

- Pomodoro Timer
- Todo List

![main_screen.png](image/main_screen.png)

![todo_list.png](image/todo_list.png)

## Build

```bash
./gradlew assembleDebug
```


## Troubleshooting

### Timer not running in background
- Grant "Background activity" permission for this app in system settings

### No notification sound
- Check notification sound settings in system preferences
- Ensure "Do Not Disturb" mode is off,or allow the app to show notifications in "Do Not Disturb" mode

### Notifications not appearing
- Grant "Notifications" permission when prompted
- Check battery optimization settings may restrict notifications

