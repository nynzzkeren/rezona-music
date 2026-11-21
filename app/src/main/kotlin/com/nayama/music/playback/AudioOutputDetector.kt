package com.nayama.music.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nayama.music.R

data class AudioOutputInfo(
    val label: String,
    @param:DrawableRes val iconResId: Int,
    val isExternalDevice: Boolean
)

object AudioOutputDetector {

    fun getCurrentOutputDevice(context: Context): AudioOutputInfo {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return AudioOutputInfo("Phone Speaker", R.drawable.ic_speaker, false)
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            // Priority check: Bluetooth -> USB DAC / Audio -> Wired Headset -> Speaker
            val bt = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
            if (bt != null) {
                val name = bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio"
                return AudioOutputInfo(name, R.drawable.ic_bluetooth, true)
            }

            val usb = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
            if (usb != null) {
                val name = usb.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC Audio"
                return AudioOutputInfo(name, R.drawable.ic_headphones, true)
            }

            val wired = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            }
            if (wired != null) {
                return AudioOutputInfo("Wired Headphones", R.drawable.ic_headphones, true)
            }

            AudioOutputInfo("Phone Speaker", R.drawable.ic_speaker, false)
        } catch (_: Throwable) {
            AudioOutputInfo("Phone Speaker", R.drawable.ic_speaker, false)
        }
    }

    fun openSystemAudioOutputPanel(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_VOLUME)
            } else {
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Throwable) {
            // Ignore if settings panel is unavailable on restricted ROMs
        }
    }
}

@Composable
fun rememberAudioOutputInfo(context: Context): AudioOutputInfo {
    var outputInfo by remember { mutableStateOf(AudioOutputDetector.getCurrentOutputDevice(context)) }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                outputInfo = AudioOutputDetector.getCurrentOutputDevice(context)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                outputInfo = AudioOutputDetector.getCurrentOutputDevice(context)
            }
        }
        try {
            audioManager?.registerAudioDeviceCallback(callback, null)
        } catch (_: Throwable) {
        }
        onDispose {
            try {
                audioManager?.unregisterAudioDeviceCallback(callback)
            } catch (_: Throwable) {
            }
        }
    }

    return outputInfo
}

