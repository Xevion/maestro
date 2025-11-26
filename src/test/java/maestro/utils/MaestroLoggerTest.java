package maestro.utils;

import static org.junit.Assert.*;

import maestro.api.utils.MaestroLogger;
import org.junit.Test;
import org.slf4j.Logger;

/** Tests for {@link MaestroLogger} logger factory. */
public class MaestroLoggerTest {

    @Test
    public void testGetValidCategory() {
        Logger log = MaestroLogger.get("path");
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
            Logger log = MaestroLogger.get(category);
            assertNotNull("Logger for category " + category + " should not be null", log);
            assertEquals(category, log.getName());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidCategoryThrows() {
        MaestroLogger.get("invalid");
    }

    @Test
    public void testInvalidCategoryMessage() {
        try {
            MaestroLogger.get("invalid");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown logger category"));
            assertTrue(e.getMessage().contains("invalid"));
        }
    }

    @Test
    public void testHasCategory() {
        assertTrue(MaestroLogger.hasCategory("path"));
        assertTrue(MaestroLogger.hasCategory("mine"));
        assertFalse(MaestroLogger.hasCategory("invalid"));
        assertFalse(MaestroLogger.hasCategory("maestro.path"));
    }

    @Test
    public void testSameLoggerInstance() {
        Logger log1 = MaestroLogger.get("path");
        Logger log2 = MaestroLogger.get("path");
        assertSame("Should return same logger instance for same category", log1, log2);
    }

    @Test
    public void testDifferentCategories() {
        Logger pathLog = MaestroLogger.get("path");
        Logger mineLog = MaestroLogger.get("mine");
        assertNotSame("Different categories should have different loggers", pathLog, mineLog);
    }
}
