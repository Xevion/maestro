package maestro.command;

import maestro.api.command.argparser.IArgParserManager;
import maestro.command.argparser.ArgParserManager;

public enum CommandSystem {
    INSTANCE;

    public IArgParserManager getParserManager() {
        return ArgParserManager.INSTANCE;
    }
}
