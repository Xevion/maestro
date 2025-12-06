package maestro.recovery

import maestro.pathing.movement.movements.MovementTraverse
import maestro.pathing.recovery.FailureReason
import maestro.pathing.recovery.MovementFailureRecord
import maestro.pathing.recovery.MovementKey
import maestro.utils.pack
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MovementFailureRecordTest {
    @Test
    fun `isExpired returns false when record is fresh`() {
        val key = MovementKey(pack(100, 64, 200), pack(150, 65, 250))
        val currentTime = System.currentTimeMillis()
        val record =
            MovementFailureRecord(
                key,
                MovementTraverse::class.java,
                currentTime,
                FailureReason.BLOCKED,
                1,
            )

        assertFalse(record.isExpired(currentTime + 1000, 30000))
    }

    @Test
    fun `isExpired returns true when record exceeds duration`() {
        val key = MovementKey(pack(100, 64, 200), pack(150, 65, 250))
        val timestamp = System.currentTimeMillis() - 60000 // 60 seconds ago
        val record =
            MovementFailureRecord(
                key,
                MovementTraverse::class.java,
                timestamp,
                FailureReason.BLOCKED,
                1,
            )

        val currentTime = System.currentTimeMillis()
        assertTrue(record.isExpired(currentTime, 30000)) // 30 second duration
    }

    @Test
    fun `data class auto-generates toString`() {
        val key = MovementKey(pack(100, 64, 200), pack(150, 65, 250))
        val record =
            MovementFailureRecord(
                key,
                MovementTraverse::class.java,
                System.currentTimeMillis(),
                FailureReason.BLOCKED,
                3,
            )

        val str = record.toString()
        assertTrue(str.contains("MovementFailureRecord"))
        assertTrue(str.contains("BLOCKED"))
        assertTrue(str.contains("attempts=3"))
    }
}
