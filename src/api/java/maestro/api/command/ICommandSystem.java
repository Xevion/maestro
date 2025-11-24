package maestro.api.command;

import maestro.api.command.argparser.IArgParserManager;

public interface ICommandSystem {

    IArgParserManager getParserManager();
}
