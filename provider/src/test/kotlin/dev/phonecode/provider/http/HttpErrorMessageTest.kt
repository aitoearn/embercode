package dev.phonecode.provider.http

import dev.phonecode.provider.domain.FailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HttpErrorMessageTest {

    @Test fun htmlPageIsNotDumped() {
        val html = "<!DOCTYPE html><html><head><title>Not Found</title></head><body>404</body></html>"
        val msg = httpErrorMessage(404, "text/html; charset=utf-8", html)
        assertEquals("HTTP 404: endpoint not found, check the provider's base URL", msg)
        assertFalse("error message must not contain raw markup", msg.contains("<"))
    }

    @Test fun extractsJsonErrorMessage() {
        val body = """{"error":{"message":"Invalid model: mimo-v2.5","type":"invalid_request_error"}}"""
        assertEquals("HTTP 400: Invalid model: mimo-v2.5", httpErrorMessage(400, "application/json", body))
    }

    @Test fun unescapesJsonEscapesInExtractedMessage() {
        val body = """{"error":{"message":"can't reach \"foo\":\n line two"}}"""
        assertEquals("HTTP 400: can't reach \"foo\":\n line two", httpErrorMessage(400, "application/json", body))
    }

    @Test fun plainTextIsKeptButTruncated() {
        assertEquals("HTTP 429: rate limited", httpErrorMessage(429, "text/plain", "rate limited"))
    }

    @Test fun nonNotFoundHtmlGivesBareStatus() {
        assertEquals("HTTP 502", httpErrorMessage(502, "text/html", "<html><body>Bad Gateway</body></html>"))
    }

    @Test fun emptyBodyGivesBareStatus() {
        assertEquals("HTTP 500", httpErrorMessage(500, null, ""))
    }

    @Test fun classifiesQuotaBeforeGenericRateLimit() {
        assertEquals(FailureKind.QUOTA, classifyFailure(429, "usage_limit_exceeded", "Go usage limit exceeded"))
        assertEquals(FailureKind.RATE_LIMIT, classifyFailure(429, null, "too many requests"))
    }

    @Test fun unsupportedCountryIsNotReportedAsBadCredentials() {
        assertEquals(
            FailureKind.INVALID_REQUEST,
            classifyFailure(403, "unsupported_country_region_territory", "Country not supported"),
        )
    }
}
