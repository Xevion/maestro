package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.exception.CommandException;
import maestro.task.ITask;
import maestro.task.PathingCommand;
import maestro.task.PathingCommandType;

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

    public ExecutionControlCommands(Agent maestro) {
        // array for mutability, non-field so reflection can't touch it
        final boolean[] paused = {false};
        maestro.getPathingControlManager()
                .registerTask(
                        new ITask() {
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
                            throw new CommandException.InvalidState("Already paused");
                        }
                        paused[0] = true;
                        log.atInfo().log("Execution paused");
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
                new Command(maestro, "resume", "r", "unpause") {
                    @Override
                    public void execute(String label, IArgConsumer args) throws CommandException {
                        args.requireMax(0);
                        maestro.getBuilderTask().resume();
                        if (!paused[0]) {
                            throw new CommandException.InvalidState("Not paused");
                        }
                        paused[0] = false;
                        log.atInfo().log("Execution resumed");
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
                        log.atInfo().addKeyValue("paused", paused[0]).log("Pause state reported");
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
                        log.atInfo().log("Pathing canceled");
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
