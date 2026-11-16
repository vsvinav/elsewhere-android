package com.elsewhere.app.ui.crew

import android.content.Context
import android.content.Intent
import com.elsewhere.app.crew.ShareCode
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Play's own scanner sheet: no camera permission of ours, no camera code of ours. */
fun scanPairCode(ctx: Context, onCode: (String) -> Unit, onFail: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(ctx, options).startScan()
        .addOnSuccessListener { b -> b.rawValue?.let(onCode) ?: onFail("That QR holds no code") }
        .addOnCanceledListener { }
        .addOnFailureListener { onFail(it.message ?: "Couldn't open the scanner") }
}

/** Hand a pair link to whatever the visitor uses to reach their friend. */
fun sharePairCode(ctx: Context, code: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "Pair with me on Elsewhere: $code\n${ShareCode.link(code)}")
    ctx.startActivity(Intent.createChooser(send, "Share your code").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
