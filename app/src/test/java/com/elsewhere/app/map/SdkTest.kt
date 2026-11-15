package com.elsewhere.app.map

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SdkTest {
    /** Tests never run the app's start-up, so the SDK must read as absent whatever key the build was given. */
    @Test fun `the SDK is not available before start-up initialises it`() = assertFalse(Sdk.available)
}
