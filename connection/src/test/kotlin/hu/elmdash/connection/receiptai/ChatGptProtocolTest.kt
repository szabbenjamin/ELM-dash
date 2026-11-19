package hu.elmdash.connection.receiptai

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import okio.Buffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30])
class ChatGptProtocolTest {
    private val pair=KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private fun b64(b:ByteArray)=Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private val jwks:JSONObject get() {
        val pub=pair.public as RSAPublicKey
        fun unsigned(n:java.math.BigInteger)=n.toByteArray().let { if(it[0]==0.toByte())it.copyOfRange(1,it.size)else it }
        return JSONObject().put("keys",JSONArray().put(JSONObject().put("kid","test").put("kty","RSA")
            .put("n",b64(unsigned(pub.modulus))).put("e",b64(unsigned(pub.publicExponent)))))
    }
    private fun claims()=JSONObject().put("iss",ChatGptProtocol.AUTH).put("aud","client").put("sub","person").put("exp",2000).put("nonce","nonce")
    private fun token(c:JSONObject):String {
        val content=b64("""{"alg":"RS256","kid":"test"}""".toByteArray())+"."+b64(c.toString().toByteArray())
        val sig=Signature.getInstance("SHA256withRSA").run { initSign(pair.private);update(content.toByteArray());sign() }
        return content+"."+b64(sig)
    }
    @Test fun verifiedIdentityRejectsWrongSignatureIssuerAudienceExpiryAndNonce() {
        assertEquals("person",ChatGptProtocol.identity(token(claims()),jwks,"client","nonce",1000).getString("sub"))
        for(c in listOf(claims().put("iss","https://example.com"),claims().put("aud","other"),claims().put("exp",1000),claims().put("nonce","other"))) {
            assertThrows(Exception::class.java) { ChatGptProtocol.identity(token(c),jwks,"client","nonce",1000) }
        }
        val original=token(claims());val pieces=original.split('.')
        val altered=pieces[0]+"."+b64(claims().put("sub","attacker").toString().toByteArray())+"."+pieces[2]
        assertThrows(Exception::class.java) { ChatGptProtocol.identity(altered,jwks,"client","nonce",1000) }
        assertFalse(ChatGptProtocol.granted(JSONObject().put("scope","openid email")))
        assertTrue(ChatGptProtocol.granted(JSONObject().put("scope",ChatGptProtocol.SCOPES)))
    }
    @Test fun pkceUsesStandardS256Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",ChatGptProtocol.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        assertFalse(ChatGptProtocol.same("a","b"))
    }
    private val receipt="""{"fuel":"95 E10","liters":20,"fuel_paid_huf":12000,"currency":"HUF","explanation":"Üzemanyag: 12500 Ft − 500 Ft kedvezmény. Kávé kizárva."}"""
    @Test fun fencedJsonIsAcceptedWithoutAcceptingSurroundingProse() {
        assertEquals(20.0,AiReceiptResult.parse("```json\n$receipt\n```").liters!!,0.0)
        assertThrows(Exception::class.java) { AiReceiptResult.parse("Commentary $receipt") }
    }
    @Test fun receiptFieldsRemainSeparateAndUncertainAmountCanStayNull() {
        assertNull(AiReceiptResult.parse(receipt).station)
        assertEquals("Minta kút, Minta utca 1.",AiReceiptResult.parse(JSONObject(receipt).put("station","Minta kút, Minta utca 1.").toString()).station)
        val parsed=AiReceiptResult.parse(receipt)
        assertEquals(20.0,parsed.liters!!,0.0);assertEquals(12000.0,parsed.paidHuf!!,0.0)
        assertNull(AiReceiptResult.parse(JSONObject(receipt).put("fuel_paid_huf",JSONObject.NULL).toString()).paidHuf)
        assertThrows(Exception::class.java) { AiReceiptResult.parse(JSONObject(receipt).put("liters",99).toString()) }
        assertThrows(Exception::class.java) { AiReceiptResult.parse(JSONObject(receipt).put("currency","EUR").toString()) }
        assertThrows(Exception::class.java) { AiReceiptResult.parse(JSONObject(receipt).put("fuel_paid_huf",100).toString()) }
    }
    @Test fun streamedTextIsUsedOnlyAfterSuccessfulCompletion() {
        val textDone=JSONObject().put("type","response.output_text.done").put("output_index",0).put("content_index",0).put("text",receipt)
        val terminal="data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[]}}\n\n"
        val textEvent="data: $textDone\n\n"
        assertEquals(20.0,ReceiptResponseStream.read(Buffer().writeUtf8(textEvent+terminal)).liters!!,0.0)
        assertThrows(Exception::class.java) { ReceiptResponseStream.read(Buffer().writeUtf8(textEvent)) }
        assertThrows(Exception::class.java) { ReceiptResponseStream.read(Buffer().writeUtf8(textEvent+"data: {\"type\":\"response.failed\"}\n\n")) }
    }
    @Test fun streamOnlyAcceptsCompletedResponseNeverPartialDeltas() {
        val done=JSONObject().put("type","response.completed").put("response",JSONObject().put("status","completed")
            .put("output",JSONArray().put(JSONObject().put("type","message").put("content",JSONArray().put(JSONObject().put("type","output_text").put("text",receipt))))))
        assertEquals(12000.0,ReceiptResponseStream.read(Buffer().writeUtf8("data: $done\n\n")).paidHuf!!,0.0)
        assertThrows(Exception::class.java) { ReceiptResponseStream.read(Buffer().writeUtf8("data: {\"type\":\"response.output_text.delta\",\"delta\":\"{}\"}\n\n")) }
        assertThrows(Exception::class.java) { ReceiptResponseStream.read(Buffer().writeUtf8("data: {\"type\":\"response.incomplete\"}\n\n")) }
    }
}
