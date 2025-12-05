package maestro.api.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.slf4j.LoggerFactory;

/**
 * Programmatically configures Maestro logger categories from Loggers enum.
 *
 * <p>This eliminates the need to manually maintain logger entries in logback.xml. Categories are
 * configured automatically during Loggers class initialization with:
 *
 * <ul>
 *   <li>Level: DEBUG
 *   <li>Appenders: CONSOLE, CHAT, JSON_FILE
 *   <li>Additivity: false (no propagation to root logger)
 * </ul>
 */
public enum LoggerConfigurator {
    ;

    private static boolean configured = false;

    /**
     * Configure all Maestro logger categories programmatically.
     *
     * <p>This method is idempotent and thread-safe. It retrieves appender references from the root
     * logger and attaches them to each category logger with DEBUG level.
     *
     * <p>If appenders are not yet defined in logback.xml, warnings are logged but configuration
     * continues with available appenders (graceful degradation).
     */
    public static synchronized void configure() {
        if (configured) {
            return;
        }

        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            // Get appender references from root logger
            Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            Appender<ILoggingEvent> consoleAppender = rootLogger.getAppender("CONSOLE");
            Appender<ILoggingEvent> chatAppender = rootLogger.getAppender("CHAT");
            Appender<ILoggingEvent> jsonFileAppender = rootLogger.getAppender("JSON_FILE");

            // Validate appenders (warn but continue with degraded behavior)
            if (consoleAppender == null) {
                System.err.println("[Maestro] WARNING: CONSOLE appender not found in logback.xml");
            }
            if (chatAppender == null) {
                System.err.println("[Maestro] WARNING: CHAT appender not found in logback.xml");
            }
            if (jsonFileAppender == null) {
                System.err.println(
                        "[Maestro] WARNING: JSON_FILE appender not found in logback.xml");
            }

            // Configure each category
            int categoryCount = 0;
            for (String category : Loggers.getAllCategories()) {
                Logger logger = context.getLogger(category);
                logger.setLevel(Level.DEBUG);
                logger.setAdditive(false);

                if (consoleAppender != null) {
                    logger.addAppender(consoleAppender);
                }
                if (chatAppender != null) {
                    logger.addAppender(chatAppender);
                }
                if (jsonFileAppender != null) {
                    logger.addAppender(jsonFileAppender);
                }

                categoryCount++;
            }

            configured = true;
            System.out.println(
                    "[Maestro] Configured "
                            + categoryCount
                            + " logger categories programmatically");

        } catch (Exception e) {
            System.err.println("[Maestro] ERROR: Failed to configure loggers: " + e.getMessage());
            e.printStackTrace(System.err);
            // Don't set configured=true so it can be retried
        }
    }

    /**
     * Reset configuration state for testing.
     *
     * <p>If detach is true, removes appenders from all category loggers before reset.
     *
     * @param detach whether to detach and stop appenders before reset
     */
    public static synchronized void reset(boolean detach) {
        if (detach && configured) {
            try {
                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
                for (String category : Loggers.getAllCategories()) {
                    Logger logger = context.getLogger(category);
                    logger.detachAndStopAllAppenders();
                    logger.setLevel(null); // Reset to inherit from root
                    logger.setAdditive(true); // Reset to default
                }
            } catch (Exception e) {
                System.err.println("[Maestro] ERROR: Failed to reset loggers: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
        configured = false;
    }

    /**
     * Check if configuration has been performed.
     *
     * @return true if configure() has completed successfully
     */
    public static boolean isConfigured() {
        return configured;
    }
}
