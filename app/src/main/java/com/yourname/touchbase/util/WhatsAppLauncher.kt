package com.yourname.touchbase.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object WhatsAppLauncher {

    /**
     * Opens WhatsApp with [message] pre-filled in the compose box for [phoneNumber].
     * WhatsApp does not expose an API to actually press send on someone's behalf —
     * the user still taps Send themselves. phoneNumber should include country code
     * (digits only after cleaning); we strip everything non-numeric except a
     * leading +.
     */
    fun openChatWithMessage(context: Context, phoneNumber: String, message: String) {
        val cleaned = cleanNumber(phoneNumber)
        val encodedMessage = Uri.encode(message)
        val uri = Uri.parse("https://wa.me/$cleaned?text=$encodedMessage")

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Fall back to WhatsApp Business, then to the generic wa.me handler
            // (browser) if neither app is installed.
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp.w4b")
                })
            } catch (e2: ActivityNotFoundException) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e3: ActivityNotFoundException) {
                    Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Fills {name} and any other supported placeholders in a template body. */
    fun renderTemplate(template: String, contactName: String): String =
        template.replace("{name}", contactName)

    private fun cleanNumber(raw: String): String {
        val hasPlus = raw.trim().startsWith("+")
        val digitsOnly = raw.filter { it.isDigit() }
        return if (hasPlus) "+$digitsOnly" else digitsOnly
    }
}
