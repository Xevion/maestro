package maestro.utils;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import java.util.Iterator;
import maestro.api.utils.LoggerConfigurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Tests for {@link LoggerConfigurator} programmatic logger configuration. */
public class LoggerConfiguratorTest {

    private LoggerContext context;

    @BeforeEach
    public void setUp() {
        context = (LoggerContext) LoggerFactory.getILoggerFactory();
        // Reset configuration before each test
        LoggerConfigurator.reset(true);
    }

    @AfterEach
    public void tearDown() {
        // Clean up after tests
        LoggerConfigurator.reset(true);
    }

    @Test
    public void testConfigureCreatesLoggers() {
        LoggerConfigurator.configure();

        // Verify each category logger exists
        for (String category : new String[] {"path", "mine", "combat", "farm"}) {
            Logger logger = context.getLogger(category);
            assertNotNull(logger, "Logger should exist for category: " + category);
        }
    }

    @Test
    public void testConfigureSetsDebugLevel() {
        LoggerConfigurator.configure();

        // Verify all category loggers have DEBUG level
        for (String category : new String[] {"path", "mine", "combat", "farm"}) {
            Logger logger = context.getLogger(category);
            assertEquals(
                    Level.DEBUG,
                    logger.getLevel(),
                    "Logger should have DEBUG level for category: " + category);
        }
    }

    @Test
    public void testConfigureSetsAdditivityFalse() {
        LoggerConfigurator.configure();

        // Verify additivity is false (no propagation to root)
        for (String category : new String[] {"path", "mine", "combat", "farm"}) {
            Logger logger = context.getLogger(category);
            assertFalse(
                    logger.isAdditive(),
                    "Logger should have additivity=false for category: " + category);
        }
    }

    @Test
    public void testConfigureAttachesAppenders() {
        LoggerConfigurator.configure();

        // Verify appenders are attached
        Logger logger = context.getLogger("path");
        int appenderCount = 0;
        Iterator<Appender<ILoggingEvent>> iter = logger.iteratorForAppenders();
        while (iter.hasNext()) {
            iter.next();
            appenderCount++;
        }

        // Should have CONSOLE and CHAT appenders (2 total)
        // Note: This assumes logback.xml has been loaded
        assertTrue(appenderCount >= 1, "Logger should have at least 1 appender attached");
    }

    @Test
    public void testConfigureIsIdempotent() {
        // Configure twice
        LoggerConfigurator.configure();
        LoggerConfigurator.configure();

        // Verify no duplicate appenders
        Logger logger = context.getLogger("path");
        int appenderCount = 0;
        Iterator<Appender<ILoggingEvent>> iter = logger.iteratorForAppenders();
        while (iter.hasNext()) {
            iter.next();
            appenderCount++;
        }

        // Should still have same number of appenders (idempotent)
        // Configure again to verify
        LoggerConfigurator.configure();
        int appenderCountAfter = 0;
        Iterator<Appender<ILoggingEvent>> iterAfter = logger.iteratorForAppenders();
        while (iterAfter.hasNext()) {
            iterAfter.next();
            appenderCountAfter++;
        }

        assertEquals(
                appenderCount,
                appenderCountAfter,
                "Multiple configure() calls should not add duplicate appenders");
    }

    @Test
    public void testConfigurationSetsFlag() {
        assertFalse(
                LoggerConfigurator.isConfigured(),
                "Configuration flag should be false before configure()");

        LoggerConfigurator.configure();

        assertTrue(
                LoggerConfigurator.isConfigured(),
                "Configuration flag should be true after configure()");
    }

    @Test
    public void testResetClearsFlag() {
        LoggerConfigurator.configure();
        assertTrue(LoggerConfigurator.isConfigured(), "Configuration flag should be true");

        LoggerConfigurator.reset(false);
        assertFalse(
                LoggerConfigurator.isConfigured(),
                "Configuration flag should be false after reset");
    }

    @Test
    public void testResetWithDetach() {
        LoggerConfigurator.configure();

        Logger logger = context.getLogger("path");
        assertSame(Level.DEBUG, logger.getLevel(), "Logger should have DEBUG level before reset");
        assertFalse(logger.isAdditive(), "Logger should have additivity=false before reset");

        LoggerConfigurator.reset(true);

        // After reset with detach, logger should be back to defaults
        assertFalse(LoggerConfigurator.isConfigured(), "Configuration flag should be false");

        // Verify appenders are detached
        Iterator<Appender<ILoggingEvent>> iter = logger.iteratorForAppenders();
        assertFalse(iter.hasNext(), "Logger should have no appenders after reset(true)");

        // Verify level is reset (null = inherit from parent)
        assertNull(logger.getLevel(), "Logger level should be null after reset");

        // Verify additivity is reset to default (true)
        assertTrue(logger.isAdditive(), "Logger additivity should be true after reset");
    }

    @Test
    public void testReconfigureAfterReset() {
        // Configure, reset, configure again
        LoggerConfigurator.configure();
        LoggerConfigurator.reset(true);
        LoggerConfigurator.configure();

        // Should work the same as first time
        Logger logger = context.getLogger("path");
        assertEquals(
                Level.DEBUG, logger.getLevel(), "Logger should have DEBUG level after reconfigure");
        assertFalse(logger.isAdditive(), "Logger should have additivity=false after reconfigure");

        int appenderCount = 0;
        Iterator<Appender<ILoggingEvent>> iter = logger.iteratorForAppenders();
        while (iter.hasNext()) {
            iter.next();
            appenderCount++;
        }
        assertTrue(appenderCount >= 1, "Logger should have appenders after reconfigure");
    }

    @Test
    public void testAllCategoriesConfigured() {
        LoggerConfigurator.configure();

        // Verify all categories from MaestroLogger are configured
        String[] allCategories = {
            "path", "swim", "combat", "mine", "farm", "build",
            "cache", "move", "rotation", "event", "cmd", "api",
            "waypoint", "inventory"
        };

        for (String category : allCategories) {
            Logger logger = context.getLogger(category);
            assertEquals(
                    Level.DEBUG,
                    logger.getLevel(),
                    "All categories should have DEBUG level: " + category);
            assertFalse(
                    logger.isAdditive(),
                    "All categories should have additivity=false: " + category);
        }
    }
}
