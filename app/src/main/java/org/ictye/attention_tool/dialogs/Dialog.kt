package org.ictye.attention_tool.dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.core.net.toUri

@SuppressLint("BatteryLife")
@Composable
fun BackgroundDialog(context: Context, onBackgroundDialogDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onBackgroundDialogDismiss,
    title = { Text("Background Restriction Detected") },
    text = { Text("To keep the timer running in the background, please disable battery optimization for this app.") },
    confirmButton = {
        TextButton(onClick = {
            val packageName = context.packageName
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:$packageName".toUri()
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:$packageName".toUri()
                    }
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
            onBackgroundDialogDismiss()
        }) {
            Text("Go to Settings")
        }
    },
    dismissButton = {
        TextButton(onClick = onBackgroundDialogDismiss) {
            Text("Cancel")
        }
    })