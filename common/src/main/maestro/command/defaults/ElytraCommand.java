package maestro.command.defaults;

import static maestro.api.command.IMaestroChatControl.FORCE_COMMAND_PREFIX;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.MaestroAPI;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.command.helpers.TabCompleteHelper;
import maestro.api.pathing.goals.Goal;
import maestro.api.process.ICustomGoalProcess;
import maestro.api.process.IElytraProcess;
import maestro.utils.chat.ChatMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

public class ElytraCommand extends Command {

    public ElytraCommand(IAgent maestro) {
        super(maestro, "elytra");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        final ICustomGoalProcess customGoalProcess = maestro.getCustomGoalProcess();
        final IElytraProcess elytra = maestro.getElytraProcess();
        if (args.hasExactlyOne() && args.peekString().equals("supported")) {
            log.atInfo().log(elytra.isLoaded() ? "yes" : unsupportedSystemMessage());
            return;
        }
        if (!elytra.isLoaded()) {
            throw new CommandException.InvalidState(unsupportedSystemMessage());
        }

        if (!args.hasAny()) {
            if (Agent.settings().elytraTermsAccepted.value) {
                if (detectOn2b2t()) {
                    warn2b2t();
                }
            } else {
                gatekeep();
            }
            Goal iGoal = customGoalProcess.mostRecentGoal();
            if (iGoal == null) {
                throw new CommandException.InvalidState("No goal has been set");
            }
            if (ctx.world().dimension() != Level.NETHER) {
                throw new CommandException.InvalidState("Only works in the nether");
            }
            try {
                elytra.pathTo(iGoal);
            } catch (IllegalArgumentException ex) {
                throw new CommandException.InvalidState(ex.getMessage());
            }
            return;
        }

        final String action = args.getString();
        switch (action) {
            case "reset":
                {
                    elytra.resetState();
                    log.atInfo().log("Reset state but still flying to same goal");
                    break;
                }
            case "repack":
                {
                    elytra.repackChunks();
                    log.atInfo().log("Queued all loaded chunks for repacking");
                    break;
                }
            default:
                {
                    throw new CommandException.InvalidState("Invalid action");
                }
        }
    }

    private void warn2b2t() {
        if (Agent.settings().elytraPredictTerrain.value) {
            long seed = Agent.settings().elytraNetherSeed.value;
            if (seed != NEW_2B2T_SEED && seed != OLD_2B2T_SEED) {
                // Send rich component to chat manually

                MutableComponent msg1 =
                        Component.literal(
                                "It looks like you're on 2b2t, but elytraNetherSeed is"
                                        + " incorrect.");
                MutableComponent prefixed1 = Component.literal("");
                prefixed1.append(ChatMessage.createCategoryPrefix("cmd"));
                prefixed1.append(" ");
                prefixed1.append(msg1);
                net.minecraft.client.Minecraft.getInstance()
                        .execute(() -> MaestroAPI.getSettings().logger.value.accept(prefixed1));

                Component msg2 = suggest2b2tSeeds();
                MutableComponent prefixed2 = Component.literal("");
                prefixed2.append(ChatMessage.createCategoryPrefix("cmd"));
                prefixed2.append(" ");
                prefixed2.append(msg2);
                net.minecraft.client.Minecraft.getInstance()
                        .execute(() -> MaestroAPI.getSettings().logger.value.accept(prefixed2));
            }
        }
    }

    private Component suggest2b2tSeeds() {
        MutableComponent clippy = Component.literal("");
        clippy.append(
                "Within a few hundred blocks of spawn/axis/highways/etc, the terrain is too"
                        + " fragmented to be predictable. Maestro Elytra will still work, just with"
                        + " backtracking. ");
        clippy.append("However, once you get more than a few thousand blocks out, you should try ");
        MutableComponent olderSeed = Component.literal("the older seed (click here)");
        olderSeed.setStyle(
                olderSeed
                        .getStyle()
                        .withUnderlined(true)
                        .withBold(true)
                        .withHoverEvent(
                                new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.literal(
                                                Agent.settings().prefix.value
                                                        + "set elytraNetherSeed "
                                                        + OLD_2B2T_SEED)))
                        .withClickEvent(
                                new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        FORCE_COMMAND_PREFIX
                                                + "set elytraNetherSeed "
                                                + OLD_2B2T_SEED)));
        clippy.append(olderSeed);
        clippy.append(
                ". Once you're further out into newer terrain generation (this includes everything"
                        + " up through 1.12), you should try ");
        MutableComponent newerSeed = Component.literal("the newer seed (click here)");
        newerSeed.setStyle(
                newerSeed
                        .getStyle()
                        .withUnderlined(true)
                        .withBold(true)
                        .withHoverEvent(
                                new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.literal(
                                                Agent.settings().prefix.value
                                                        + "set elytraNetherSeed "
                                                        + NEW_2B2T_SEED)))
                        .withClickEvent(
                                new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        FORCE_COMMAND_PREFIX
                                                + "set elytraNetherSeed "
                                                + NEW_2B2T_SEED)));
        clippy.append(newerSeed);
        clippy.append(
                ". Once you get into 1.19 terrain, the terrain becomes unpredictable again, due to"
                    + " custom non-vanilla generation, and you should set #elytraPredictTerrain to"
                    + " false. ");
        return clippy;
    }

    private void gatekeep() {
        MutableComponent gatekeep = Component.literal("");
        gatekeep.append("To disable this message, enable the setting elytraTermsAccepted\n");
        gatekeep.append(
                "Maestro Elytra is an experimental feature. It is only intended for long distance"
                    + " travel in the Nether using fireworks for vanilla boost. It will not work"
                    + " with any other mods (\"hacks\") for non-vanilla boost. ");
        MutableComponent gatekeep2 =
                Component.literal(
                        "If you want Maestro to attempt to take off from the ground for you, you"
                                + " can enable the elytraAutoJump setting (not advisable on laggy"
                                + " servers!). ");
        gatekeep2.setStyle(
                gatekeep2
                        .getStyle()
                        .withHoverEvent(
                                new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.literal(
                                                Agent.settings().prefix.value
                                                        + "set elytraAutoJump true"))));
        gatekeep.append(gatekeep2);
        MutableComponent gatekeep3 =
                Component.literal(
                        "If you want Maestro to go slower, enable the elytraConserveFireworks"
                                + " setting and/or decrease the elytraFireworkSpeed setting. ");
        gatekeep3.setStyle(
                gatekeep3
                        .getStyle()
                        .withHoverEvent(
                                new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.literal(
                                                Agent.settings().prefix.value
                                                        + "set elytraConserveFireworks true\n"
                                                        + Agent.settings().prefix.value
                                                        + "set elytraFireworkSpeed 0.6\n"
                                                        + "(the 0.6 number is just an example,"
                                                        + " tweak to your liking)"))));
        gatekeep.append(gatekeep3);
        MutableComponent gatekeep4 = Component.literal("Maestro Elytra ");
        MutableComponent red = Component.literal("wants to know the seed");
        red.setStyle(
                red.getStyle().withColor(ChatFormatting.RED).withUnderlined(true).withBold(true));
        gatekeep4.append(red);
        gatekeep4.append(
                " of the world you are in. If it doesn't have the correct seed, it will frequently"
                        + " backtrack. It uses the seed to generate terrain far beyond what you can"
                        + " see, since terrain obstacles in the Nether can be much larger than your"
                        + " render distance. ");
        gatekeep.append(gatekeep4);
        gatekeep.append("\n");
        if (detectOn2b2t()) {
            MutableComponent gatekeep5 = Component.literal("It looks like you're on 2b2t. ");
            gatekeep5.append(suggest2b2tSeeds());
            if (!Agent.settings().elytraPredictTerrain.value) {
                gatekeep5.append(
                        Agent.settings().prefix.value
                                + "elytraPredictTerrain is currently disabled. ");
            } else {
                if (Agent.settings().elytraNetherSeed.value == NEW_2B2T_SEED) {
                    gatekeep5.append("You are using the newer seed. ");
                } else if (Agent.settings().elytraNetherSeed.value == OLD_2B2T_SEED) {
                    gatekeep5.append("You are using the older seed. ");
                } else {
                    gatekeep5.append("Defaulting to the newer seed. ");
                    Agent.settings().elytraNetherSeed.value = NEW_2B2T_SEED;
                }
            }
            gatekeep.append(gatekeep5);
        } else {
            if (Agent.settings().elytraNetherSeed.value == NEW_2B2T_SEED) {
                MutableComponent gatekeep5 =
                        Component.literal(
                                "Maestro doesn't know the seed of your world. Set it with: "
                                        + Agent.settings().prefix.value
                                        + "set elytraNetherSeed seedgoeshere\n");
                gatekeep5.append(
                        "For the time being, elytraPredictTerrain is defaulting to false since the"
                                + " seed is unknown.");
                gatekeep.append(gatekeep5);
                Agent.settings().elytraPredictTerrain.value = false;
            } else {
                if (Agent.settings().elytraPredictTerrain.value) {
                    MutableComponent gatekeep5 =
                            Component.literal(
                                    "Maestro Elytra is predicting terrain assuming that "
                                            + Agent.settings().elytraNetherSeed.value
                                            + " is the correct seed. Change that with "
                                            + Agent.settings().prefix.value
                                            + "set elytraNetherSeed seedgoeshere, or disable it"
                                            + " with "
                                            + Agent.settings().prefix.value
                                            + "set elytraPredictTerrain false");
                    gatekeep.append(gatekeep5);
                } else {
                    MutableComponent gatekeep5 =
                            Component.literal(
                                    "Maestro Elytra is not predicting terrain. If you don't know"
                                        + " the seed, this is the correct thing to do. If you do"
                                        + " know the seed, input it with "
                                            + Agent.settings().prefix.value
                                            + "set elytraNetherSeed seedgoeshere, and then enable"
                                            + " it with "
                                            + Agent.settings().prefix.value
                                            + "set elytraPredictTerrain true");
                    gatekeep.append(gatekeep5);
                }
            }
        }
        // Send rich component to chat manually
        MutableComponent prefixed = Component.literal("");
        prefixed.append(ChatMessage.createCategoryPrefix("cmd"));
        prefixed.append(" ");
        prefixed.append(gatekeep);
        net.minecraft.client.Minecraft.getInstance()
                .execute(() -> MaestroAPI.getSettings().logger.value.accept(prefixed));
    }

    private boolean detectOn2b2t() {
        ServerData data = ctx.minecraft().getCurrentServer();
        return data != null && data.ip.toLowerCase(java.util.Locale.ROOT).contains("2b2t.org");
    }

    private static final long OLD_2B2T_SEED = -4100785268875389365L;
    private static final long NEW_2B2T_SEED = 146008555100680L;

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        TabCompleteHelper helper = new TabCompleteHelper();
        if (args.hasExactlyOne()) {
            helper.append("reset", "repack", "supported");
        }
        return helper.filterPrefix(args.getString()).stream();
    }

    @Override
    public String getShortDesc() {
        return "elytra time";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The elytra command tells maestro to, in the nether, automatically fly to the"
                        + " current goal.",
                "",
                "Usage:",
                "> elytra - fly to the current goal",
                "> elytra reset - Resets the state of the process, but will try to keep flying to"
                        + " the same goal.",
                "> elytra repack - Queues all of the chunks in render distance to be given to the"
                        + " native library.",
                "> elytra supported - Tells you if maestro ships a native library that is"
                        + " compatible with your PC.");
    }

    private static String unsupportedSystemMessage() {
        final String osArch = System.getProperty("os.arch");
        final String osName = System.getProperty("os.name");
        return String.format(
                "Failed loading native library. Your CPU is %s and your operating system is %s."
                    + " Supported architectures are 64 bit x86, and 64 bit ARM. Supported operating"
                    + " systems are Windows, Linux, and Mac",
                osArch, osName);
    }
}
