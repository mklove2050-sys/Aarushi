package com.example.action

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log

data class ActionResult(
    val success: Boolean,
    val message: String,
    val actionType: String
)

class DeviceActionHandler(private val context: Context) {

    companion object {
        private const val TAG = "DeviceActionHandler"

        // Safe allowlist of applications
        private val ALLOWED_APPS = mapOf(
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "youtube" to listOf("com.google.android.youtube"),
            "instagram" to listOf("com.instagram.android"),
            "chrome" to listOf("com.android.chrome"),
            "maps" to listOf("com.google.android.apps.maps"),
            "gmail" to listOf("com.google.android.gm"),
            "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
            "spotify" to listOf("com.spotify.music"),
            "camera" to listOf("camera_intent"),
            "settings" to listOf("settings_intent"),
            "calendar" to listOf("com.google.android.calendar")
        )
    }

    fun openWhatsApp(): ActionResult {
        Log.d(TAG, "Executing openWhatsApp action")
        val pm = context.packageManager
        val candidates = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in candidates) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ActionResult(true, "WhatsApp opened successfully.", "openWhatsApp")
            }
        }

        // Try direct URI intent fallback
        return try {
            val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send"))
            uriIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (uriIntent.resolveActivity(pm) != null) {
                context.startActivity(uriIntent)
                ActionResult(true, "WhatsApp opened.", "openWhatsApp")
            } else {
                ActionResult(false, "WhatsApp is not installed on this device.", "openWhatsApp")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WhatsApp: ${e.message}", e)
            ActionResult(false, "Could not open WhatsApp: ${e.message}", "openWhatsApp")
        }
    }

    fun openApp(appName: String): ActionResult {
        val cleanName = appName.trim().lowercase()
        Log.d(TAG, "Executing openApp for '$cleanName'")

        if (cleanName.contains("whatsapp")) {
            return openWhatsApp()
        }

        // Find package mapping
        val matchingEntry = ALLOWED_APPS.entries.find { (key, _) ->
            cleanName.contains(key) || key.contains(cleanName)
        }

        if (matchingEntry == null) {
            Log.w(TAG, "App '$appName' is not in the safe allowlist")
            return ActionResult(
                false,
                "I can only open safe supported apps like WhatsApp, YouTube, Instagram, Maps, Chrome, Camera, Calculator, and Settings.",
                "openApp"
            )
        }

        val pm = context.packageManager

        // Handle special intents
        if (matchingEntry.value.contains("camera_intent")) {
            return try {
                val camIntent = Intent("android.media.action.STILL_IMAGE_CAMERA")
                camIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(camIntent)
                ActionResult(true, "Camera opened.", "openApp")
            } catch (e: Exception) {
                ActionResult(false, "Could not open Camera: ${e.message}", "openApp")
            }
        }

        if (matchingEntry.value.contains("settings_intent")) {
            return try {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS)
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settingsIntent)
                ActionResult(true, "Settings opened.", "openApp")
            } catch (e: Exception) {
                ActionResult(false, "Could not open Settings: ${e.message}", "openApp")
            }
        }

        for (pkg in matchingEntry.value) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ActionResult(true, "${matchingEntry.key.replaceFirstChar { it.uppercase() }} opened successfully.", "openApp")
            }
        }

        return ActionResult(
            false,
            "${matchingEntry.key.replaceFirstChar { it.uppercase() }} does not appear to be installed on this device.",
            "openApp"
        )
    }

    fun openUrl(url: String): ActionResult {
        Log.d(TAG, "Executing openUrl: $url")
        var validUrl = url.trim()
        if (!validUrl.startsWith("http://") && !validUrl.startsWith("https://")) {
            validUrl = "https://$validUrl"
        }

        return try {
            val uri = Uri.parse(validUrl)
            if (uri.scheme != "http" && uri.scheme != "https") {
                return ActionResult(false, "Invalid web URL protocol. Only http/https are allowed.", "openUrl")
            }

            val browserIntent = Intent(Intent.ACTION_VIEW, uri)
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(browserIntent)
            ActionResult(true, "Opened $validUrl in browser.", "openUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: ${e.message}", e)
            ActionResult(false, "Could not open URL: ${e.message}", "openUrl")
        }
    }

    fun makeCall(phoneNumber: String): ActionResult {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        Log.d(TAG, "Executing makeCall to: $cleanNumber")

        if (cleanNumber.isEmpty()) {
            return ActionResult(false, "Please provide a valid phone number.", "makeCall")
        }

        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber"))
            dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(dialIntent)
            ActionResult(true, "Initiated call to $cleanNumber on phone dialer.", "makeCall")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dial number: ${e.message}", e)
            ActionResult(false, "Could not make call: ${e.message}", "makeCall")
        }
    }

    data class ContactInfo(val name: String, val phoneNumber: String)

    fun callContact(contactName: String): ActionResult {
        Log.d(TAG, "Executing callContact for name: '$contactName'")
        val search = contactName.trim()
        if (search.isEmpty()) {
            return ActionResult(false, "Please provide a contact name.", "callContact")
        }

        val hasContactPermission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!hasContactPermission) {
            return ActionResult(
                false,
                "Contacts permission is required to search contacts. Please grant Contacts permission.",
                "callContact"
            )
        }

        val contacts = mutableListOf<ContactInfo>()
        var cursor: Cursor? = null
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$search%")

            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.let {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext() && contacts.size < 10) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                    val number = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                    if (name.isNotEmpty() && number.isNotEmpty()) {
                        // Avoid duplicates
                        if (contacts.none { c -> c.phoneNumber == number }) {
                            contacts.add(ContactInfo(name, number))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts: ${e.message}", e)
            return ActionResult(false, "Error reading contacts: ${e.message}", "callContact")
        } finally {
            cursor?.close()
        }

        return when {
            contacts.isEmpty() -> {
                ActionResult(false, "I couldn't find any contact matching '$search'.", "callContact")
            }
            contacts.size == 1 -> {
                val match = contacts[0]
                val dialResult = makeCall(match.phoneNumber)
                if (dialResult.success) {
                    ActionResult(true, "Calling ${match.name} at ${match.phoneNumber}.", "callContact")
                } else {
                    dialResult
                }
            }
            else -> {
                // Multiple matches - do not guess! Ask user
                val listSummary = contacts.take(3).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                ActionResult(
                    false,
                    "I found multiple contacts for '$search': $listSummary. Which one would you like to call?",
                    "callContact"
                )
            }
        }
    }
}
