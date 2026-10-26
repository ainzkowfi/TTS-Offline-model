package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VoiceCloneDialog(
    onDismiss: () -> Unit,
    onCloneFromUri: (Uri, String, String) -> Unit,
    onCloneFromSamples: (ShortArray, String, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var voiceName by remember { mutableStateOf("") }
    var selectedAccent by remember { mutableStateOf("American") }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableFloatStateOf(0f) }
    var audioRecordJob by remember { mutableStateOf<Job?>(null) }
    var recordedSamples by remember { mutableStateOf<ShortArray?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Audio file picker
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAudioUri = uri
            recordedSamples = null
            var name = "reference_speech.wav"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIdx)
                }
            }
            selectedFileName = name
            if (voiceName.isBlank()) {
                voiceName = name.substringBeforeLast(".").replace('_', ' ')
            }
        }
    }

    // Permission launcher for microphone
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Start recording
            isRecording = true
            recordingDurationSec = 0f
            selectedAudioUri = null
            selectedFileName = null

            audioRecordJob = coroutineScope.launch(Dispatchers.IO) {
                val sampleRate = 16000
                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                var recorder: AudioRecord? = null
                val recordedList = ArrayList<Short>()

                try {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        recorder = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                        )
                        recorder.startRecording()

                        val buffer = ShortArray(1024)
                        val startTime = System.currentTimeMillis()

                        while (isActive && isRecording) {
                            val read = recorder.read(buffer, 0, buffer.size)
                            if (read > 0) {
                                for (k in 0 until read) {
                                    recordedList.add(buffer[k])
                                }
                            }
                            val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                            withContext(Dispatchers.Main) {
                                recordingDurationSec = elapsed
                            }
                            if (elapsed >= 15f) break // Max 15 seconds
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VoiceCloneDialog", "Error recording audio", e)
                } finally {
                    try {
                        recorder?.stop()
                        recorder?.release()
                    } catch (ignored: Exception) {}
                    withContext(Dispatchers.Main) {
                        isRecording = false
                        if (recordedList.isNotEmpty()) {
                            recordedSamples = recordedList.toShortArray()
                            if (voiceName.isBlank()) {
                                voiceName = "My Cloned Voice"
                            }
                        }
                    }
                }
            }
        } else {
            Toast.makeText(context, "Microphone permission is required to record speech", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioRecordJob?.cancel()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isProcessing) {
                audioRecordJob?.cancel()
                onDismiss()
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = ElectricViolet
                )
                Text(
                    text = "Kokoro Inno-Clone Voice Tuner",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Zero-shot neural voice cloning powered by remsky/kokoro-inno-clone-tuner. Provide a 5–10 second reference speech sample.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                OutlinedTextField(
                    value = voiceName,
                    onValueChange = { voiceName = it },
                    label = { Text("Cloned Voice Name") },
                    placeholder = { Text("e.g. Narrator Morgan, Custom Bella") },
                    modifier = Modifier.fillMaxWidth().testTag("clone_voice_name_input"),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("American", "British").forEach { accent ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedAccent == accent) ElectricViolet else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedAccent = accent }
                        ) {
                            Text(
                                text = "$accent Accent",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (selectedAccent == accent) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Recording or Audio File Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Reference Audio Input",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Record Button
                            Button(
                                onClick = {
                                    if (isRecording) {
                                        isRecording = false
                                        audioRecordJob?.cancel()
                                    } else {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) StatusRed else ElectricViolet
                                ),
                                modifier = Modifier.testTag("record_audio_button")
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isRecording) "Stop (${recordingDurationSec.toInt()}s)" else "Record (5s)")
                            }

                            // Pick File Button
                            OutlinedButton(
                                onClick = { audioPicker.launch("audio/*") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("pick_audio_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = NeonCyan
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick Audio", color = NeonCyan)
                            }
                        }

                        // Selected Audio Indicator
                        if (selectedFileName != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Ready: $selectedFileName",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = StatusGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        } else if (recordedSamples != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Recorded ${recordedSamples!!.size / 16000}s reference speech",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = StatusGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }

                if (isProcessing) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Analyzing pitch ceiling & prosody vectors...",
                            style = MaterialTheme.typography.labelSmall.copy(color = NeonCyan)
                        )
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = NeonCyan
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val uri = selectedAudioUri
                    val samples = recordedSamples
                    if (uri != null) {
                        isProcessing = true
                        onCloneFromUri(uri, voiceName, selectedAccent)
                        onDismiss()
                    } else if (samples != null) {
                        isProcessing = true
                        onCloneFromSamples(samples, voiceName, selectedAccent)
                        onDismiss()
                    } else {
                        Toast.makeText(context, "Please record or select a reference audio clip first", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isProcessing && (selectedAudioUri != null || recordedSamples != null),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                modifier = Modifier.testTag("submit_clone_voice_button")
            ) {
                Text("Enroll Cloned Voice", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    audioRecordJob?.cancel()
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}
