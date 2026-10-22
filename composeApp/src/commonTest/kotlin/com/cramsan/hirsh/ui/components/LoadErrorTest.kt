package com.cramsan.hirsh.ui.components

import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadErrorTest {

    @Test
    fun `a 403 means the role can't read it`() {
        assertEquals(LoadError.FORBIDDEN, ApiException(ApiError.Forbidden).toLoadError())
    }

    @Test
    fun `any other error response is a failed load`() {
        assertEquals(LoadError.FAILED, ApiException(ApiError.Unknown("Internal Server Error")).toLoadError())
        assertEquals(LoadError.FAILED, ApiException(ApiError.NotFound()).toLoadError())
    }

    @Test
    fun `an exception that isn't an error response means the server wasn't reached`() {
        assertEquals(LoadError.UNREACHABLE, RuntimeException("Connection refused").toLoadError())
    }
}
