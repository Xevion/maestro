package maestro.utils;

import static org.junit.Assert.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import java.util.Iterator;
import maestro.api.utils.LoggerConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/** Tests for {@link LoggerConfigurator} programmatic logger configuration. */
public class LoggerConfiguratorTest {

    private LoggerContext context;

    @Before
    public void setUp() {
        context = (LoggerContext) LoggerFactory.getILoggerFactory();
        // Reset configuration before each test
        LoggerConfigurator.reset(true);
    }

    @After
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
            assertNotNull("Logger should exist for category: " + category, logger);
        }
    }

    @Test
    public void testConfigureSetsDebugLevel() {
        LoggerConfigurator.configure();

        // Verify all category loggers have DEBUG level
        for (String category : new String[] {"path", "mine", "combat", "farm"}) {
            Logger logger = context.getLogger(category);
            assertEquals(
                    "Logger should have DEBUG level for category: " + category,
                    Level.DEBUG,
                    logger.getLevel());
        }
    }

    @Test
    public void testConfigureSetsAdditivityFalse() {
        LoggerConfigurator.configure();

        // Verify additivity is false (no propagation to root)
        for (String category : new String[] {"path", "mine", "combat", "farm"}) {
            Logger logger = context.getLogger(category);
            assertFalse(
                    "Logger should have additivity=false for category: " + category,
                    logger.isAdditive());
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
        assertTrue("Logger should have at least 1 appender attached", appenderCount >= 1);
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
                "Multiple configure() calls should not add duplicate appenders",
                appenderCount,
                appenderCountAfter);
    }

    @Test
    public void testConfigurationSetsFlag() {
        assertFalse(
                "Configuration flag should be false before configure()",
                LoggerConfigurator.isConfigured());

        LoggerConfigurator.configure();

        assertTrue(
                "Configuration flag should be true after configure()",
                LoggerConfigurator.isConfigured());
    }

    @Test
    public void testResetClearsFlag() {
        LoggerConfigurator.configure();
        assertTrue("Configuration flag should be true", LoggerConfigurator.isConfigured());

        LoggerConfigurator.reset(false);
        assertFalse(
                "Configuration flag should be false after reset",
                LoggerConfigurator.isConfigured());
    }

    @Test
    public void testResetWithDetach() {
        LoggerConfigurator.configure();

        Logger logger = context.getLogger("path");
        assertSame("Logger should have DEBUG level before reset", logger.getLevel(), Level.DEBUG);
        assertFalse("Logger should have additivity=false before reset", logger.isAdditive());

        LoggerConfigurator.reset(true);

        // After reset with detach, logger should be back to defaults
        assertFalse("Configuration flag should be false", LoggerConfigurator.isConfigured());

        // Verify appenders are detached
        Iterator<Appender<ILoggingEvent>> iter = logger.iteratorForAppenders();
        assertFalse("Logger should have no appenders after reset(true)", iter.hasNext());

        // Verify level is reset (null = inherit from parent)
        assertNull("Logger level should be null after reset", logger.getLevel());

        // Verify additivity is reset to default (true)
        assertTrue("Logger additivity should be true after reset", logger.isAdditive());
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
                "Logger should have DEBUG level after reconfigure", Level.DEBUG, logger.getLevel());
        assertFalse("Logger should have additivity=false after reconfigure", logger.isAdditive());

        int appenderCount = 0;
        Iterator<Appender<ILoggingEvent>> iter = logger.iteratorForAppenders();
        while (iter.hasNext()) {
            iter.next();
            appenderCount++;
        }
        assertTrue("Logger should have appenders after reconfigure", appenderCount >= 1);
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
                    "All categories should have DEBUG level: " + category,
                    Level.DEBUG,
                    logger.getLevel());
            assertFalse(
                    "All categories should have additivity=false: " + category,
                    logger.isAdditive());
        }
    }
}
