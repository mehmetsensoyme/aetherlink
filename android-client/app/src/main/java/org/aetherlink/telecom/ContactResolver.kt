package org.aetherlink.telecom

import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log

data class ResolvedContact(
    val contactName: String,
    val phoneNumber: String,
    val isKnown: Boolean
)

object ContactResolver {
    private const val TAG = "ContactResolver"
    const val UNKNOWN_NUMBER = "Bilinmeyen Numara"

    /**
     * Resolves contact name and clean formatted number from Android Contacts Provider.
     * Uses ContactsContract.PhoneLookup as primary query, with CommonDataKinds.Phone fallback.
     */
    fun resolve(context: Context, rawNumber: String?): ResolvedContact {
        val cleaned = cleanNumber(rawNumber)
        if (cleaned.isNullOrBlank()) {
            return ResolvedContact(
                contactName = UNKNOWN_NUMBER,
                phoneNumber = UNKNOWN_NUMBER,
                isKnown = false
            )
        }

        var matchedName: String? = null

        // 1. Primary Query: ContactsContract.PhoneLookup
        try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(cleaned)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.NUMBER
            )
            context.contentResolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) {
                            matchedName = name
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ContactsContract.PhoneLookup query failed for $cleaned: ${e.message}")
        }

        // 2. Fallback Query: ContactsContract.CommonDataKinds.Phone
        if (matchedName.isNullOrBlank()) {
            try {
                val digitsOnly = cleaned.replace("[^0-9+]".toRegex(), "")
                if (digitsOnly.length >= 7) {
                    val suffix = digitsOnly.takeLast(7)
                    val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                    val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                    val selectionArgs = arrayOf("%$suffix")
                    val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

                    context.contentResolver.query(phoneUri, projection, selection, selectionArgs, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                            if (nameIdx >= 0) {
                                val name = cursor.getString(nameIdx)
                                if (!name.isNullOrBlank()) {
                                    matchedName = name
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "CommonDataKinds.Phone fallback query failed: ${e.message}")
            }
        }

        val formatted = formatNumber(cleaned)
        val hasName = !matchedName.isNullOrBlank()
        val finalName = if (hasName) matchedName!! else formatted

        return ResolvedContact(
            contactName = finalName,
            phoneNumber = formatted,
            isKnown = hasName
        )
    }

    /**
     * Attempts to query the most recent outgoing call record from CallLog.Calls.
     * Useful on modern Android versions where ACTION_NEW_OUTGOING_CALL is restricted.
     */
    fun getLatestOutgoingCall(context: Context): ResolvedContact? {
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
                "${CallLog.Calls.TYPE} = ?",
                arrayOf(CallLog.Calls.OUTGOING_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)

                    val number = if (numIdx >= 0) it.getString(numIdx) else null
                    val cachedName = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val date = if (dateIdx >= 0) it.getLong(dateIdx) else 0L

                    // Check if outgoing record was placed in the last 60 seconds
                    val ageMs = System.currentTimeMillis() - date
                    if (!number.isNullOrBlank() && Math.abs(ageMs) < 60000L) {
                        val resolved = resolve(context, number)
                        if (!cachedName.isNullOrBlank() && !resolved.isKnown) {
                            return ResolvedContact(
                                contactName = cachedName,
                                phoneNumber = resolved.phoneNumber,
                                isKnown = true
                            )
                        }
                        return resolved
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CallLog.Calls latest outgoing query failed: ${e.message}")
        }
        return null
    }

    /**
     * Resolves contact name and phone number from dialer notification title and text.
     * Accurately extracts contact name when One UI / Pixel notification displays caller name in title.
     */
    fun resolveFromNotification(context: Context, title: String, text: String): ResolvedContact {
        val trimmedTitle = title.trim()
        val trimmedText = text.trim()

        val isGenericTitle = trimmedTitle.equals("Gelen Arama", ignoreCase = true) ||
                trimmedTitle.equals("Giden Arama", ignoreCase = true) ||
                trimmedTitle.equals("Bilinmeyen Numara", ignoreCase = true) ||
                trimmedTitle.equals("Unknown Caller", ignoreCase = true) ||
                trimmedTitle.equals("Incoming Call", ignoreCase = true) ||
                trimmedTitle.equals("Outgoing Call", ignoreCase = true)

        val hasLettersInTitle = trimmedTitle.any { it.isLetter() }

        // If title contains letters and is not a generic placeholder, it's the contact's name!
        if (hasLettersInTitle && !isGenericTitle) {
            val phoneCandidate = cleanNumber(trimmedText)
            val formattedPhone = if (!phoneCandidate.isNullOrBlank()) formatNumber(phoneCandidate) else trimmedText
            return ResolvedContact(
                contactName = trimmedTitle,
                phoneNumber = if (formattedPhone.isNotBlank()) formattedPhone else UNKNOWN_NUMBER,
                isKnown = true
            )
        }

        // If title is a phone number, resolve it via contacts provider
        val cleanedTitle = cleanNumber(trimmedTitle)
        if (!cleanedTitle.isNullOrBlank()) {
            val resolved = resolve(context, cleanedTitle)
            if (resolved.isKnown) {
                return resolved
            }
            return ResolvedContact(
                contactName = formatNumber(cleanedTitle),
                phoneNumber = formatNumber(cleanedTitle),
                isKnown = false
            )
        }

        // Otherwise check text
        val cleanedText = cleanNumber(trimmedText)
        if (!cleanedText.isNullOrBlank()) {
            return resolve(context, cleanedText)
        }

        return ResolvedContact(
            contactName = if (!isGenericTitle && trimmedTitle.isNotBlank()) trimmedTitle else UNKNOWN_NUMBER,
            phoneNumber = UNKNOWN_NUMBER,
            isKnown = false
        )
    }

    private fun cleanNumber(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty() ||
            trimmed.equals("null", ignoreCase = true) ||
            trimmed.equals("unknown", ignoreCase = true) ||
            trimmed.equals("private", ignoreCase = true) ||
            trimmed.equals("restricted", ignoreCase = true) ||
            trimmed.equals("Numara Çevriliyor", ignoreCase = true) ||
            trimmed.equals("Giden Arama", ignoreCase = true) ||
            trimmed.equals(UNKNOWN_NUMBER, ignoreCase = true) ||
            trimmed == "-1" || trimmed == "-2"
        ) {
            return null
        }
        return trimmed
    }

    fun formatNumber(raw: String): String {
        return try {
            val formatted = PhoneNumberUtils.formatNumber(raw, "TR")
            if (!formatted.isNullOrBlank()) formatted else raw
        } catch (_: Exception) {
            raw
        }
    }
}
