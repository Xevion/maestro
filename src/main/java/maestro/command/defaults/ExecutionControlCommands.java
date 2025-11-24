package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.command.exception.CommandInvalidStateException;
import maestro.api.process.IMaestroProcess;
import maestro.api.process.PathingCommand;
import maestro.api.process.PathingCommandType;

/**
 * Contains the pause, resume, and paused commands.
 *
 * <p>This thing is scoped to hell, private so far you can't even access it using reflection,
 * because you AREN'T SUPPOSED TO USE THIS to pause and resume Maestro. Make your own process that
 * returns {@link PathingCommandType#REQUEST_PAUSE REQUEST_PAUSE} as needed.
 */
public class ExecutionControlCommands {

    Command pauseCommand;
    Command resumeCommand;
    Command pausedCommand;
    Command cancelCommand;

    public ExecutionControlCommands(IAgent maestro) {
        // array for mutability, non-field so reflection can't touch it
        final boolean[] paused = {false};
        maestro.getPathingControlManager()
                .registerProcess(
                        new IMaestroProcess() {
                            @Override
                            public boolean isActive() {
                                return paused[0];
                            }

                            @Override
                            public PathingCommand onTick(
                                    boolean calcFailed, boolean isSafeToCancel) {
                                maestro.getInputOverrideHandler().clearAllKeys();
                                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                            }

                            @Override
                            public boolean isTemporary() {
                                return true;
                            }

                            @Override
                            public void onLostControl() {}

                            @Override
                            public double priority() {
                                return DEFAULT_PRIORITY + 1;
                            }

                            @Override
                            public String displayName0() {
                                return "Pause/Resume Commands";
                            }
                        });
        pauseCommand =
                new Command(maestro, "pause", "p", "paws") {
                    @Override
                    public void execute(String label, IArgConsumer args) throws CommandException {
                        args.requireMax(0);
                        if (paused[0]) {
                            throw new CommandInvalidStateException("Already paused");
                        }
                        paused[0] = true;
                        logDirect("Paused");
                    }

                    @Override
                    public Stream<String> tabComplete(String label, IArgConsumer args) {
                        return Stream.empty();
                    }

                    @Override
                    public String getShortDesc() {
                        return "Pauses Maestro until you use resume";
                    }

                    @Override
                    public List<String> getLongDesc() {
                        return Arrays.asList(
                                "The pause command tells Maestro to temporarily stop whatever it's"
                                        + " doing.",
                                "",
                                "This can be used to pause pathing, building, following, whatever."
                                    + " A single use of the resume command will start it right back"
                                    + " up again!",
                                "",
                                "Usage:",
                                "> pause");
                    }
                };
        resumeCommand =
                new Command(maestro, "resume", "r", "unpause", "unpaws") {
                    @Override
                    public void execute(String label, IArgConsumer args) throws CommandException {
                        args.requireMax(0);
                        maestro.getBuilderProcess().resume();
                        if (!paused[0]) {
                            throw new CommandInvalidStateException("Not paused");
                        }
                        paused[0] = false;
                        logDirect("Resumed");
                    }

                    @Override
                    public Stream<String> tabComplete(String label, IArgConsumer args) {
                        return Stream.empty();
                    }

                    @Override
                    public String getShortDesc() {
                        return "Resumes Maestro after a pause";
                    }

                    @Override
                    public List<String> getLongDesc() {
                        return Arrays.asList(
                                "The resume command tells Maestro to resume whatever it was doing"
                                        + " when you last used pause.",
                                "",
                                "Usage:",
                                "> resume");
                    }
                };
        pausedCommand =
                new Command(maestro, "paused") {
                    @Override
                    public void execute(String label, IArgConsumer args) throws CommandException {
                        args.requireMax(0);
                        logDirect(String.format("Maestro is %spaused", paused[0] ? "" : "not "));
                    }

                    @Override
                    public Stream<String> tabComplete(String label, IArgConsumer args) {
                        return Stream.empty();
                    }

                    @Override
                    public String getShortDesc() {
                        return "Tells you if Maestro is paused";
                    }

                    @Override
                    public List<String> getLongDesc() {
                        return Arrays.asList(
                                "The paused command tells you if Maestro is currently paused by"
                                        + " use of the pause command.",
                                "",
                                "Usage:",
                                "> paused");
                    }
                };
        cancelCommand =
                new Command(maestro, "cancel", "c", "stop") {
                    @Override
                    public void execute(String label, IArgConsumer args) throws CommandException {
                        args.requireMax(0);
                        if (paused[0]) {
                            paused[0] = false;
                        }
                        maestro.getPathingBehavior().cancelEverything();
                        logDirect("ok canceled");
                    }

                    @Override
                    public Stream<String> tabComplete(String label, IArgConsumer args) {
                        return Stream.empty();
                    }

                    @Override
                    public String getShortDesc() {
                        return "Cancel what Maestro is currently doing";
                    }

                    @Override
                    public List<String> getLongDesc() {
                        return Arrays.asList(
                                "The cancel command tells Maestro to stop whatever it's currently"
                                        + " doing.",
                                "",
                                "Usage:",
                                "> cancel");
                    }
                };
    }
}
