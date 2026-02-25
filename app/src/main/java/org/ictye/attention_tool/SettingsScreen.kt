package org.ictye.attention_tool

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import org.ictye.attention_tool.utils.TodoManager
import java.io.File

@Preview(showBackground = true)
@Composable
fun SettingScreenPreview() {
    SettingsScreen(onNavigateBack = {})
}


@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    
    var showPomodoroSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showTroubleshooting by remember { mutableStateOf(false) }
    var workMinutes by rememberSaveable { mutableStateOf("25") }
    var shortBreakMinutes by rememberSaveable { mutableStateOf("5") }
    var longBreakMinutes by rememberSaveable { mutableStateOf("15") }
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    val todoFile = File(context.filesDir, "todo.txt")
                    todoFile.writeText(content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    if (showPomodoroSettings) {
        AlertDialog(
            onDismissRequest = { showPomodoroSettings = false },
            title = { Text("Pomodoro Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = workMinutes,
                        onValueChange = { workMinutes = it },
                        label = { Text("Work Duration (minutes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = shortBreakMinutes,
                        onValueChange = { shortBreakMinutes = it },
                        label = { Text("Short Break (minutes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = longBreakMinutes,
                        onValueChange = { longBreakMinutes = it },
                        label = { Text("Long Break (minutes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    mainActivity?.clockServiceBinder?.service?.setDurations(
                        (workMinutes.toLongOrNull() ?: 25) * 60 * 1000L,
                        (shortBreakMinutes.toLongOrNull() ?: 5) * 60 * 1000L,
                        (longBreakMinutes.toLongOrNull() ?: 15) * 60 * 1000L
                    )
                    mainActivity?.clockServiceBinder?.service?.resetTimer()
                    showPomodoroSettings = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPomodoroSettings = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("About") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Attention Tool", fontWeight = FontWeight.Bold)
                    Text("Version 1.0.0")
                    Text("By Ictye")
                    Text("GPL License")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A Pomodoro Timer with Todo list functionality.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showTroubleshooting) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Troubleshooting") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Timer not running in background?", fontWeight = FontWeight.Bold)
                    Text("1. Make sure battery optimization is disabled for this app")
                    Text("2. Check that the app has notification permission")
                    Text("3. Ensure \"Show notifications\" is enabled in app settings")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Notification not showing?", fontWeight = FontWeight.Bold)
                    Text("1. Grant notification permission when prompted")
                    Text("2. Check system notification settings")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Todo list not saving?", fontWeight = FontWeight.Bold)
                    Text("1. Check app storage permissions")
                    Text("2. Try restarting the app")
                }
            },
            confirmButton = {
                TextButton(onClick = { showTroubleshooting = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        ListItem(
            headlineContent = { Text("Pomodoro Timer") },
            supportingContent = { Text("Configure work/break durations") },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.clock),
                    contentDescription = "Pomodoro Timer Setting"
                )
            },
            modifier = Modifier.clickable { showPomodoroSettings = true }
        )
        
        HorizontalDivider()
        
        ListItem(
            headlineContent = { Text("Export Todo") },
            supportingContent = { Text("Share todo.txt file") },
            leadingContent = {
                Icon(Icons.Default.Share, contentDescription = null)
            },
            modifier = Modifier.clickable {
                val file = File(TodoManager.getTodoFilePath())
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export Todo"))
                }
            }
        )
        
        HorizontalDivider()
        
        ListItem(
            headlineContent = { Text("Import Todo") },
            supportingContent = { Text("Import todo.txt file") },
            leadingContent = {
                Icon(Icons.Default.Refresh, contentDescription = null)
            },
            modifier = Modifier.clickable {
                importLauncher.launch(arrayOf("text/plain"))
            }
        )
        
        HorizontalDivider()
        
        ListItem(
            headlineContent = { Text("Troubleshooting") },
            supportingContent = { Text("Common issues and solutions") },
            leadingContent = {
                Icon(Icons.Default.Warning, contentDescription = null)
            },
            modifier = Modifier.clickable { showTroubleshooting = true }
        )
        
        HorizontalDivider()
        
        ListItem(
            headlineContent = { Text("About") },
            supportingContent = { Text("App information") },
            leadingContent = {
                Icon(Icons.Default.Info, contentDescription = null)
            },
            modifier = Modifier.clickable { showAbout = true }
        )
    }
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    var workMinutes by rememberSaveable { mutableStateOf("25") }
    var shortBreakMinutes by rememberSaveable { mutableStateOf("5") }
    var longBreakMinutes by rememberSaveable { mutableStateOf("15") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = workMinutes,
                    onValueChange = { workMinutes = it },
                    label = { Text("Work Duration (minutes)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shortBreakMinutes,
                    onValueChange = { shortBreakMinutes = it },
                    label = { Text("Short Break (minutes)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = longBreakMinutes,
                    onValueChange = { longBreakMinutes = it },
                    label = { Text("Long Break (minutes)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                mainActivity?.clockServiceBinder?.service?.setDurations(
                    (workMinutes.toLongOrNull() ?: 25) * 60 * 1000L,
                    (shortBreakMinutes.toLongOrNull() ?: 5) * 60 * 1000L,
                    (longBreakMinutes.toLongOrNull() ?: 15) * 60 * 1000L
                )
                mainActivity?.clockServiceBinder?.service?.resetTimer()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
