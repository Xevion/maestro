package maestro.command;

import maestro.command.argparser.ArgParserManager;
import maestro.command.argparser.IArgParserManager;

public enum CommandSystem {
    INSTANCE;

    public IArgParserManager getParserManager() {
        return ArgParserManager.INSTANCE;
    }
}
