package maestro.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/** Tests for {@link Loggers} logger factory. */
public class LoggersTest {

    @Test
    public void testGetValidCategory() {
        Logger log = Loggers.Path.get();
        assertNotNull(log);
        assertEquals("path", log.getName());
    }

    @Test
    public void testAllCategoriesExist() {
        Loggers[] allLoggers = {
            Loggers.Path,
            Loggers.Swim,
            Loggers.Combat,
            Loggers.Mine,
            Loggers.Farm,
            Loggers.Build,
            Loggers.Cache,
            Loggers.Move,
            Loggers.Rotation,
            Loggers.Event,
            Loggers.Cmd,
            Loggers.Api
        };

        for (Loggers loggerEnum : allLoggers) {
            Logger log = loggerEnum.get();
            assertNotNull(log, "Logger for " + loggerEnum + " should not be null");
        }
    }

    @Test
    public void testFromCategory() {
        assertEquals(Loggers.Path, Loggers.fromCategory("path"));
        assertEquals(Loggers.Mine, Loggers.fromCategory("mine"));
        assertNull(Loggers.fromCategory("invalid"));
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
        Logger log1 = Loggers.Path.get();
        Logger log2 = Loggers.Path.get();
        assertSame(log1, log2, "Should return same logger instance for same category");
    }

    @Test
    public void testDifferentCategories() {
        Logger pathLog = Loggers.Path.get();
        Logger mineLog = Loggers.Mine.get();
        assertNotSame(pathLog, mineLog, "Different categories should have different loggers");
    }
}
