package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun appMetadata_isConfigured() {
        assertEquals("com.example", MainActivity::class.java.`package`?.name)
    }
}
