package maestro.recovery

import maestro.pathing.recovery.RetryBudget
import maestro.utils.pack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RetryBudgetTest {
    private lateinit var budget: RetryBudget

    @BeforeEach
    fun setup() {
        budget = RetryBudget()
    }

    @Test
    fun `canRetry returns true initially`() {
        val position = pack(100, 64, 200)
        assertTrue(budget.canRetry(position))
    }

    @Test
    fun `canRetry returns true after one retry`() {
        val position = pack(100, 64, 200)
        budget.recordRetry(position)
        assertTrue(budget.canRetry(position))
    }

    @Test
    fun `canRetry returns false after max retries`() {
        val position = pack(100, 64, 200)

        // Record 3 retries (MAX_RETRIES = 3)
        budget.recordRetry(position)
        budget.recordRetry(position)
        budget.recordRetry(position)

        assertFalse(budget.canRetry(position))
    }

    @Test
    fun `getRetryCount tracks attempts correctly`() {
        val position = pack(100, 64, 200)

        assertEquals(0, budget.getRetryCount(position))

        budget.recordRetry(position)
        assertEquals(1, budget.getRetryCount(position))

        budget.recordRetry(position)
        assertEquals(2, budget.getRetryCount(position))
    }

    @Test
    fun `reset clears all retry counts`() {
        val pos1 = pack(100, 64, 200)
        val pos2 = pack(150, 65, 250)

        budget.recordRetry(pos1)
        budget.recordRetry(pos2)

        assertEquals(1, budget.getRetryCount(pos1))
        assertEquals(1, budget.getRetryCount(pos2))

        budget.reset()

        assertEquals(0, budget.getRetryCount(pos1))
        assertEquals(0, budget.getRetryCount(pos2))
    }

    @Test
    fun `different positions have independent budgets`() {
        val pos1 = pack(100, 64, 200)
        val pos2 = pack(150, 65, 250)

        budget.recordRetry(pos1)
        budget.recordRetry(pos1)
        budget.recordRetry(pos1)

        assertFalse(budget.canRetry(pos1))
        assertTrue(budget.canRetry(pos2))
    }
}
