package app.restora.live

import app.restora.live.ui.Field
import app.restora.live.ui.validateForm
import app.restora.live.ui.visibleFormFields
import org.junit.Assert.*
import org.junit.Test

class FormValidationTest {
    @Test fun invalidMoneyCannotBecomeANonFiniteJsonNumber() {
        val fields = listOf(Field("amount", "Amount", true, "number"))
        for (value in listOf("NaN", "Infinity", "1e309", "abc", "")) {
            val (answer, error) = validateForm(fields, mapOf("amount" to value))
            assertNull(answer); assertNotNull(error)
        }
    }
    @Test fun valuesKeepTheExistingBackendContract() {
        val (answer, error) = validateForm(listOf(Field("name", "Name", true), Field("amount", "Amount", true, "number"), Field("tags", "Tags", type = "list")), mapOf("name" to " Ubaid ", "amount" to "1500.50", "tags" to "VIP, Regular\nFamily"))
        assertNull(error)
        assertEquals("Ubaid", answer?.getString("name"))
        assertEquals(1500.5, answer?.getDouble("amount") ?: 0.0, 0.0)
        assertEquals(3, answer?.getJSONArray("tags")?.length())
    }
    @Test fun asyncChoiceArrivalKeepsNewGuestFieldsVisibleByDefault() {
        val fields = listOf(Field("customerId", "Guest", initial = ""), Field("newName", "New guest", whenField = "customerId", whenValues = setOf("")))
        assertTrue(visibleFormFields(fields, emptyMap()).any { it.key == "newName" })
        assertFalse(visibleFormFields(fields, mapOf("customerId" to "saved-guest")).any { it.key == "newName" })
    }

}
