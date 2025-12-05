package maestro.utils;

import static org.junit.jupiter.api.Assertions.*;

import maestro.api.utils.Loggers;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/** Tests for {@link Loggers} logger factory. */
public class LoggersTest {

    @Test
    public void testGetValidCategory() {
        Logger log = Loggers.get("path");
        assertNotNull(log);
        assertEquals("path", log.getName());
    }

    @Test
    public void testAllCategoriesExist() {
        String[] categories = {
            "path",
            "swim",
            "combat",
            "mine",
            "farm",
            "build",
            "cache",
            "move",
            "rotation",
            "event",
            "cmd",
            "api"
        };

        for (String category : categories) {
            Logger log = Loggers.get(category);
            assertNotNull(log, "Logger for category " + category + " should not be null");
            assertEquals(category, log.getName());
        }
    }

    @Test
    public void testInvalidCategoryThrows() {
        assertThrows(IllegalArgumentException.class, () -> Loggers.get("invalid"));
    }

    @Test
    public void testInvalidCategoryMessage() {
        try {
            Loggers.get("invalid");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown logger category"));
            assertTrue(e.getMessage().contains("invalid"));
        }
    }

    @Test
    public void testHasCategory() {
        assertTrue(Loggers.hasCategory("path"));
        assertTrue(Loggers.hasCategory("mine"));
        assertFalse(Loggers.hasCategory("invalid"));
        assertFalse(Loggers.hasCategory("maestro.path"));
    }

    @Test
    public void testSameLoggerInstance() {
        Logger log1 = Loggers.get("path");
        Logger log2 = Loggers.get("path");
        assertSame(log1, log2, "Should return same logger instance for same category");
    }

    @Test
    public void testDifferentCategories() {
        Logger pathLog = Loggers.get("path");
        Logger mineLog = Loggers.get("mine");
        assertNotSame(pathLog, mineLog, "Different categories should have different loggers");
    }
}
