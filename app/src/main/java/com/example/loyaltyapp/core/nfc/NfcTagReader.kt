package com.example.loyaltyapp.core.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag

/**
 * Thin wrapper around [NfcAdapter] reader mode — no manifest intent-filter or foreground
 * dispatch needed, unlike the classic NFC APIs. Scoped to a screen: call [start] when a
 * "tap the tag" screen is shown and [stop] when it's left (see `NfcScanScreen`), matching how
 * the handover doc describes NFC as one alternate way to pick a customer, not an always-on
 * listener.
 */
class NfcTagReader(private val activity: Activity) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val isAvailable: Boolean get() = adapter != null

    /** [onTagRead] is invoked off the main thread (NFC reader callback thread) — hop back to the main/coroutine dispatcher in the caller. */
    fun start(onTagRead: (String) -> Unit) {
        adapter?.enableReaderMode(
            activity,
            { tag: Tag -> tag.uidHex()?.let(onTagRead) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    fun stop() {
        adapter?.disableReaderMode(activity)
    }

    private fun Tag.uidHex(): String? {
        val bytes = id ?: return null
        return bytes.joinToString("") { "%02X".format(it) }
    }
}

/** Unwraps a Compose [Context] (often a wrapped one) down to the hosting [Activity], or null if none. */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
