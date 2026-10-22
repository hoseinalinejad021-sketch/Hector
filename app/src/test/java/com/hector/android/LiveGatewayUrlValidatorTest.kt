package com.hector.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveGatewayUrlValidatorTest {
    @Test fun acceptsHttpsWorkerLiveUrl() {
        assertTrue(LiveGatewayUrlValidator.isValid("https://hector.example.workers.dev/live"))
        assertTrue(LiveGatewayUrlValidator.isValid("https://voice.example.com:8443/live"))
    }

    @Test fun rejectsInsecureOrUnconfiguredUrls() {
        assertFalse(LiveGatewayUrlValidator.isValid(""))
        assertFalse(LiveGatewayUrlValidator.isValid("http://hector.example.workers.dev/live"))
        assertFalse(LiveGatewayUrlValidator.isValid("https:///live"))
    }

    @Test fun rejectsCredentialsAndUnexpectedRoutes() {
        assertFalse(LiveGatewayUrlValidator.isValid("https://user:secret@hector.example.workers.dev/live"))
        assertFalse(LiveGatewayUrlValidator.isValid("https://hector.example.workers.dev/"))
        assertFalse(LiveGatewayUrlValidator.isValid("https://hector.example.workers.dev/live/session"))
        assertFalse(LiveGatewayUrlValidator.isValid("https://hector.example.workers.dev/live/"))
    }

    @Test fun rejectsQueryFragmentsAndInvalidPorts() {
        assertFalse(LiveGatewayUrlValidator.isValid("https://hector.example.workers.dev/live?token=x"))
        assertFalse(LiveGatewayUrlValidator.isValid("https://hector.example.workers.dev/live#fragment"))
        assertFalse(LiveGatewayUrlValidator.isValid("https://hector.example.workers.dev:70000/live"))
        assertFalse(LiveGatewayUrlValidator.isValid("https://hector.example.workers.dev:0/live"))
    }
}
