package io.github.lacostej.playtime.auth

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import io.github.lacostej.playtime.FakeNintendo
import io.github.lacostej.playtime.FixedClock
import io.github.lacostej.playtime.api.ApiError
import io.github.lacostej.playtime.bodyText
import io.github.lacostej.playtime.config.NintendoApi
import io.github.lacostej.playtime.json

class NintendoAuthTest {
    @Test
    fun pkceChallengeMatchesRfc7636Example() {
        // RFC 7636, appendix B
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun loginUrlCarriesTheConfiguredClientAndAFreshState() {
        val auth = NintendoAuth(FakeNintendo { json("{}") }.client)
        val a = auth.startLogin()
        val b = auth.startLogin()
        val url = Url(a.url)
        assertEquals("accounts.nintendo.com", url.host)
        assertEquals(NintendoApi.CLIENT_ID, url.parameters["client_id"])
        assertEquals(NintendoApi.REDIRECT_URI, url.parameters["redirect_uri"])
        assertEquals(NintendoApi.SCOPES.joinToString(" "), url.parameters["scope"])
        assertEquals(pkceChallenge(a.verifier), url.parameters["session_token_code_challenge"])
        assertEquals(a.state, url.parameters["state"])
        assertEquals(50, a.verifier.length)
        assert(a.verifier != b.verifier && a.state != b.state)
    }

    @Test
    fun redirectLinkIsParsedAndItsStateChecked() {
        val auth = NintendoAuth(FakeNintendo { json("{}") }.client)
        val link = "npf54789befb391a838://auth#session_state=xyz&session_token_code=CODE123&state=STATE"
        assertEquals("CODE123", auth.sessionTokenCode("  $link\n", "STATE"))
        assertFailsWith<IllegalArgumentException> { auth.sessionTokenCode(link, "OTHER") }
        assertFailsWith<IllegalArgumentException> { auth.sessionTokenCode("npf54789befb391a838://auth#state=STATE", "STATE") }
    }

    @Test
    fun sessionTokenExchangeSendsCodeAndVerifier() = runTest {
        val fake = FakeNintendo { json("""{"session_token":"SESSION","code":"x"}""") }
        assertEquals("SESSION", NintendoAuth(fake.client).exchangeSessionToken("CODE", "VERIFIER"))
        val body = fake.requests.single().bodyText()
        assertContains(body, "session_token_code=CODE")
        assertContains(body, "session_token_code_verifier=VERIFIER")
        assertEquals(NintendoApi.SESSION_TOKEN_URL, fake.requests.single().url.toString())
    }

    @Test
    fun refreshUsesTheIdTokenAndComputesExpiry() = runTest {
        val fake = FakeNintendo { json("""{"id_token":"ID","access_token":"ACCESS","expires_in":900,"scope":["openid"]}""") }
        val token = NintendoAuth(fake.client, FixedClock(1_000)).refresh("SESSION")
        assertEquals("ID", token.idToken)
        assertEquals(1_900, token.expiresAt.epochSeconds)
        assertContains(fake.requests.single().bodyText(), "\"session_token\":\"SESSION\"")
        assertContains(fake.requests.single().bodyText(), NintendoApi.GRANT_TYPE)
    }

    @Test
    fun rejectedSessionMeansLoginAgain() = runTest {
        val fake = FakeNintendo { json("""{"error":"invalid_grant"}""", status = 400) }
        assertFailsWith<ApiError.LoginExpired> { NintendoAuth(fake.client).refresh("OLD") }
    }
}
