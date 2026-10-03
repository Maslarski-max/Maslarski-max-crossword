package com.maslarski.crossword.data.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class PurchaseSignatureVerifierTest {

    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val verifier = PurchaseSignatureVerifier(Base64.getEncoder().encodeToString(keys.public.encoded))
    private val data = """{"productId":"crossword_coins_1000","purchaseToken":"t1","purchaseState":0}"""

    private fun sign(text: String): String = Base64.getEncoder().encodeToString(
        Signature.getInstance("SHA1withRSA").run {
            initSign(keys.private)
            update(text.toByteArray())
            sign()
        },
    )

    @Test
    fun `accepts data signed with the licence key`() {
        assertTrue(verifier.verify(data, sign(data)))
    }

    @Test
    fun `rejects tampered data, foreign signatures and garbage`() {
        assertFalse(verifier.verify(data.replace("t1", "t2"), sign(data)))
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val foreign = Base64.getEncoder().encodeToString(
            Signature.getInstance("SHA1withRSA").run {
                initSign(other.private)
                update(data.toByteArray())
                sign()
            },
        )
        assertFalse(verifier.verify(data, foreign))
        assertFalse(verifier.verify(data, "not base64!"))
        assertFalse(verifier.verify(data, ""))
    }

    @Test
    fun `rejects everything when no key is configured`() {
        val missing = PurchaseSignatureVerifier("")
        assertFalse(missing.configured)
        assertFalse(missing.verify(data, sign(data)))
        assertFalse(PurchaseSignatureVerifier("bogus").verify(data, sign(data)))
    }
}
