package maestro.api;

import maestro.api.utils.SettingsUtil;

/**
 * Exposes the {@link IMaestroProvider} instance and the {@link Settings} instance for API usage.
 */
public final class MaestroAPI {

    private static final IMaestroProvider provider;
    private static final Settings settings;

    static {
        settings = new Settings();
        SettingsUtil.readAndApply(settings, SettingsUtil.SETTINGS_DEFAULT_NAME);

        try {
            provider =
                    (IMaestroProvider)
                            Class.forName("maestro.MaestroProvider")
                                    .getDeclaredConstructor()
                                    .newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static IMaestroProvider getProvider() {
        return MaestroAPI.provider;
    }

    public static Settings getSettings() {
        return MaestroAPI.settings;
    }
}
