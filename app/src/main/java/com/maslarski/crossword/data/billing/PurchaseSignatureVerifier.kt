package com.maslarski.crossword.data.billing

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Checks that purchase data was signed by Google Play with the app's licence key
 * (Play Console > Monetization setup > Licensing). With no key configured every purchase is rejected.
 */
class PurchaseSignatureVerifier(base64PublicKey: String) {

    private val key: PublicKey? = runCatching {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey.trim())))
    }.getOrNull()

    val configured: Boolean get() = key != null

    fun verify(signedData: String, signature: String): Boolean {
        val publicKey = key ?: return false
        return runCatching {
            Signature.getInstance("SHA1withRSA").run {
                initVerify(publicKey)
                update(signedData.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(signature))
            }
        }.getOrDefault(false)
    }
}
