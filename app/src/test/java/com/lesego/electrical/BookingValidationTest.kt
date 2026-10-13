package com.lesego.electrical

import com.lesego.electrical.utils.ValidationUtils
import org.junit.Assert.*
import org.junit.Test

class BookingValidationTest {
    @Test fun validEmailIsAccepted() { assertTrue(ValidationUtils.isValidEmail("customer@example.com")) }
    @Test fun invalidEmailIsRejected() { assertFalse(ValidationUtils.isValidEmail("invalid-email")) }
}
