package maestro.command;

import maestro.api.command.ICommandSystem;
import maestro.api.command.argparser.IArgParserManager;
import maestro.command.argparser.ArgParserManager;

public enum CommandSystem implements ICommandSystem {
    INSTANCE;

    @Override
    public IArgParserManager getParserManager() {
        return ArgParserManager.INSTANCE;
    }
}
