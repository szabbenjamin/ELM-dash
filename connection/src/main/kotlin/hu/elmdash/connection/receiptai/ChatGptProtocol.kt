package hu.elmdash.connection.receiptai

import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

internal object ChatGptProtocol {
    const val AUTH="https://auth.openai.com"
    const val API="https://api.openai.com/v1"
    const val SCOPES="openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    fun random()=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun challenge(verifier:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
    fun same(a:String,b:String)=MessageDigest.isEqual(a.toByteArray(),b.toByteArray())
    fun identity(token:String, jwks:JSONObject, client:String, nonce:String?, now:Long=System.currentTimeMillis()/1000):JSONObject {
        val parts=token.split('.'); require(parts.size==3) { "Érvénytelen belépési válasz." }
        fun decode(s:String)=Base64.getUrlDecoder().decode(s)
        val header=JSONObject(String(decode(parts[0]),Charsets.UTF_8))
        require(header.getString("alg")=="RS256")
        val keys=jwks.getJSONArray("keys")
        val jwk=(0 until keys.length()).map { keys.getJSONObject(it) }.single { it.optString("kid")==header.getString("kid") && it.optString("kty")=="RSA" }
        val key=KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(BigInteger(1,decode(jwk.getString("n"))),BigInteger(1,decode(jwk.getString("e")))))
        require(Signature.getInstance("SHA256withRSA").run { initVerify(key); update((parts[0]+"."+parts[1]).toByteArray()); verify(decode(parts[2])) })
        val claims=JSONObject(String(decode(parts[1]),Charsets.UTF_8))
        val audiences=claims.optJSONArray("aud")?.let { a->(0 until a.length()).map { a.getString(it) } } ?: listOf(claims.getString("aud"))
        require(claims.getString("iss")==AUTH && client in audiences && claims.getLong("exp")>now)
        if (audiences.size>1 || claims.has("azp")) require(claims.optString("azp")==client)
        if (claims.has("nbf")) require(claims.getLong("nbf")<=now+60)
        if(nonce!=null) require(same(claims.getString("nonce"),nonce))
        require(claims.getString("sub").isNotBlank())
        return claims
    }
    fun granted(token:JSONObject) = token.optString("scope").split(' ').containsAll(listOf("chatgpt.tokens.use.direct","resource.invoke"))
}

data class AiReceipt(val fuel:String?, val liters:Double?, val paidHuf:Double?, val explanation:String, val station:String? = null)
internal object AiReceiptResult {
    fun parse(text:String):AiReceipt {
        val clean=text.trim().let { value ->
            if(value.startsWith("```json\n") && value.endsWith("```")) value.removePrefix("```json\n").removeSuffix("```").trim()
            else if(value.startsWith("```\n") && value.endsWith("```")) value.removePrefix("```\n").removeSuffix("```").trim()
            else value
        }
        val j=JSONObject(clean)
        fun number(key:String):Double? = if(j.isNull(key)) null else j.getDouble(key).also { require(it.isFinite() && it>0) }
        val liters=number("liters");val paid=number("fuel_paid_huf")
        if(liters!=null && liters>45)throw ReceiptAiException("A felismert mennyiség meghaladja a 45 literes tankot. Ellenőrizd a blokkot; add meg kézzel a helyes adatot.")
        if(paid!=null && paid>90000)throw ReceiptAiException("A felismert üzemanyagösszeg túl nagy. Ellenőrizd és add meg kézzel.")
        if(liters!=null && paid!=null && paid/liters !in 200.0..2000.0)throw ReceiptAiException("A felismert liter és összeg nem ad életszerű literárat. Ellenőrizd és add meg kézzel.")
        if(j.optString("currency")!="HUF")throw ReceiptAiException("A blokk forintos pénzneme nem igazolható. Add meg kézzel az adatokat.")
        return AiReceipt(if(j.isNull("fuel"))null else j.getString("fuel").take(120),liters,paid,j.optString("explanation").take(2000), if(j.isNull("station")) null else j.optString("station").trim().take(240).takeIf { it.isNotBlank() })
    }
}
