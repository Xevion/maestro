package maestro.command.defaults;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.BufferBuilder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.datatypes.ForAxis;
import maestro.command.datatypes.ForBlockOptionalMeta;
import maestro.command.datatypes.ForDirection;
import maestro.command.datatypes.RelativeBlockPos;
import maestro.command.exception.CommandException;
import maestro.command.helpers.TabCompleteHelper;
import maestro.event.events.RenderEvent;
import maestro.event.listener.AbstractGameEventListener;
import maestro.pathing.BlockStateInterface;
import maestro.rendering.IRenderer;
import maestro.schematic.*;
import maestro.schematic.mask.shape.CylinderMask;
import maestro.schematic.mask.shape.SphereMask;
import maestro.selection.Selection;
import maestro.selection.SelectionManager;
import maestro.task.schematic.StaticSchematic;
import maestro.utils.BlockOptionalMeta;
import maestro.utils.BlockOptionalMetaLookup;
import maestro.utils.PackedBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class SelCommand extends Command {

    private SelectionManager manager = maestro.getSelectionManager();
    private PackedBlockPos pos1 = null;
    private ISchematic clipboard = null;
    private Vec3i clipboardOffset = null;

    public SelCommand(Agent maestro) {
        super(maestro, "sel", "selection", "s");
        maestro.getGameEventHandler()
                .registerEventListener(
                        new AbstractGameEventListener() {
                            @Override
                            public void onRenderPass(RenderEvent event) {
                                if (!Agent.getPrimaryAgent()
                                                .getSettings()
                                                .renderSelectionCorners
                                                .value
                                        || pos1 == null) {
                                    return;
                                }
                                Color color =
                                        Agent.getPrimaryAgent()
                                                .getSettings()
                                                .colorSelectionPos1
                                                .value;
                                float opacity =
                                        Agent.getPrimaryAgent()
                                                .getSettings()
                                                .selectionOpacity
                                                .value;
                                float lineWidth =
                                        Agent.getPrimaryAgent()
                                                .getSettings()
                                                .selectionLineWidth
                                                .value;
                                boolean ignoreDepth =
                                        Agent.getPrimaryAgent()
                                                .getSettings()
                                                .renderSelectionIgnoreDepth
                                                .value;
                                BufferBuilder bufferBuilder =
                                        IRenderer.startLines(
                                                color, opacity, lineWidth, ignoreDepth);
                                IRenderer.emitAABB(
                                        bufferBuilder,
                                        event.modelViewStack,
                                        new AABB(pos1.toBlockPos()));
                                IRenderer.endLines(bufferBuilder, ignoreDepth);
                            }
                        });
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Action action = Action.getByName(args.getString());
        if (action == null) {
            throw new CommandException.InvalidArgument.InvalidType(args.consumed(), "an action");
        }
        if (action == Action.POS1 || action == Action.POS2) {
            if (action == Action.POS2 && pos1 == null) {
                throw new CommandException.InvalidState("Set pos1 first before using pos2");
            }
            PackedBlockPos playerPos = ctx.viewerPos();
            PackedBlockPos pos =
                    args.hasAny()
                            ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos)
                            : playerPos;
            args.requireMax(0);
            if (action == Action.POS1) {
                pos1 = pos;
                log.atInfo().log("Position 1 has been set");
            } else {
                manager.addSelection(pos1, pos);
                pos1 = null;
                log.atInfo().log("Selection added");
            }
        } else if (action == Action.CLEAR) {
            args.requireMax(0);
            pos1 = null;
            log.atInfo().log(
                    String.format("Removed %d selections", manager.removeAllSelections().length));
        } else if (action == Action.UNDO) {
            args.requireMax(0);
            if (pos1 != null) {
                pos1 = null;
                log.atInfo().log("Undid pos1");
            } else {
                Selection[] selections = manager.getSelections();
                if (selections.length < 1) {
                    throw new CommandException.InvalidState("Nothing to undo!");
                } else {
                    pos1 = manager.removeSelection(selections[selections.length - 1]).pos1();
                    log.atInfo().log("Undid pos2");
                }
            }
        } else if (action.isFillAction()) {
            BlockOptionalMeta type =
                    action == Action.CLEARAREA
                            ? new BlockOptionalMeta(Blocks.AIR)
                            : args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);

            final BlockOptionalMetaLookup replaces; // Action.REPLACE
            final Direction.Axis alignment; // Action.(H)CYLINDER
            if (action == Action.REPLACE) {
                args.requireMin(1);
                List<BlockOptionalMeta> replacesList = new ArrayList<>();
                replacesList.add(type);
                while (args.has(2)) {
                    replacesList.add(args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE));
                }
                type = args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
                replaces =
                        new BlockOptionalMetaLookup(replacesList.toArray(new BlockOptionalMeta[0]));
                alignment = null;
            } else if (action == Action.CYLINDER || action == Action.HCYLINDER) {
                args.requireMax(1);
                alignment =
                        args.hasAny() ? args.getDatatypeFor(ForAxis.INSTANCE) : Direction.Axis.Y;
                replaces = null;
            } else {
                args.requireMax(0);
                replaces = null;
                alignment = null;
            }
            Selection[] selections = manager.getSelections();
            if (selections.length == 0) {
                throw new CommandException.InvalidState("No selections");
            }
            PackedBlockPos origin = selections[0].min();
            CompositeSchematic composite = new CompositeSchematic(0, 0, 0);
            for (Selection selection : selections) {
                PackedBlockPos min = selection.min();
                origin =
                        new PackedBlockPos(
                                Math.min(origin.getX(), min.getX()),
                                Math.min(origin.getY(), min.getY()),
                                Math.min(origin.getZ(), min.getZ()));
            }
            for (Selection selection : selections) {
                Vec3i size = selection.size();
                PackedBlockPos min = selection.min();

                // Java 8 so no switch expressions 😿
                UnaryOperator<ISchematic> create =
                        fill -> {
                            final int w = fill.widthX();
                            final int h = fill.heightY();
                            final int l = fill.lengthZ();

                            return switch (action) {
                                case WALLS -> new WallsSchematic(fill);
                                case SHELL -> new ShellSchematic(fill);
                                case REPLACE -> new ReplaceSchematic(fill, replaces);
                                case SPHERE ->
                                        MaskSchematic.create(
                                                fill, new SphereMask(w, h, l, true).compute());
                                case HSPHERE ->
                                        MaskSchematic.create(
                                                fill, new SphereMask(w, h, l, false).compute());
                                case CYLINDER ->
                                        MaskSchematic.create(
                                                fill,
                                                new CylinderMask(w, h, l, true, alignment)
                                                        .compute());
                                case HCYLINDER ->
                                        MaskSchematic.create(
                                                fill,
                                                new CylinderMask(w, h, l, false, alignment)
                                                        .compute());
                                default ->
                                        // Silent fail
                                        fill;
                            };
                        };

                ISchematic schematic =
                        create.apply(
                                new FillSchematic(size.getX(), size.getY(), size.getZ(), type));
                composite.put(
                        schematic,
                        min.getX() - origin.getX(),
                        min.getY() - origin.getY(),
                        min.getZ() - origin.getZ());
            }
            maestro.getBuilderTask().build("Fill", composite, origin.toBlockPos());
            log.atInfo().log("Filling now");
        } else if (action == Action.COPY) {
            PackedBlockPos playerPos = ctx.viewerPos();
            PackedBlockPos pos =
                    args.hasAny()
                            ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos)
                            : playerPos;
            args.requireMax(0);
            Selection[] selections = manager.getSelections();
            if (selections.length < 1) {
                throw new CommandException.InvalidState("No selections");
            }
            BlockStateInterface bsi = new BlockStateInterface(ctx);
            PackedBlockPos origin = selections[0].min();
            CompositeSchematic composite = new CompositeSchematic(0, 0, 0);
            for (Selection selection : selections) {
                PackedBlockPos min = selection.min();
                origin =
                        new PackedBlockPos(
                                Math.min(origin.getX(), min.getX()),
                                Math.min(origin.getY(), min.getY()),
                                Math.min(origin.getZ(), min.getZ()));
            }
            for (Selection selection : selections) {
                Vec3i size = selection.size();
                PackedBlockPos min = selection.min();
                BlockState[][][] blockstates =
                        new BlockState[size.getX()][size.getZ()][size.getY()];
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            blockstates[x][z][y] =
                                    bsi.get0(min.getX() + x, min.getY() + y, min.getZ() + z);
                        }
                    }
                }
                ISchematic schematic = new StaticSchematic(blockstates);
                composite.put(
                        schematic,
                        min.getX() - origin.getX(),
                        min.getY() - origin.getY(),
                        min.getZ() - origin.getZ());
            }
            clipboard = composite;
            clipboardOffset = origin.toBlockPos().subtract(pos.toBlockPos());
            log.atInfo().log("Selection copied");
        } else if (action == Action.PASTE) {
            PackedBlockPos playerPos = ctx.viewerPos();
            PackedBlockPos pos =
                    args.hasAny()
                            ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos)
                            : playerPos;
            args.requireMax(0);
            if (clipboard == null) {
                throw new CommandException.InvalidState("You need to copy a selection first");
            }
            maestro.getBuilderTask()
                    .build("Fill", clipboard, pos.offset(clipboardOffset).toBlockPos());
            log.atInfo().log("Building now");
        } else if (action == Action.EXPAND || action == Action.CONTRACT || action == Action.SHIFT) {
            args.requireExactly(3);
            TransformTarget transformTarget = TransformTarget.getByName(args.getString());
            if (transformTarget == null) {
                throw new CommandException.InvalidState("Invalid transform type");
            }
            Direction direction = args.getDatatypeFor(ForDirection.INSTANCE);
            int blocks = args.getAs(Integer.class);
            Selection[] selections = manager.getSelections();
            if (selections.length < 1) {
                throw new CommandException.InvalidState("No selections found");
            }
            selections = transformTarget.transform(selections);
            for (Selection selection : selections) {
                if (action == Action.EXPAND) {
                    manager.expand(selection, direction, blocks);
                } else if (action == Action.CONTRACT) {
                    manager.contract(selection, direction, blocks);
                } else {
                    manager.shift(selection, direction, blocks);
                }
            }
            log.atInfo().log(String.format("Transformed %d selections", selections.length));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                            .append(Action.getAllNames())
                            .filterPrefix(args.getString())
                            .sortAlphabetically()
                            .stream();
        } else {
            Action action = Action.getByName(args.getString());
            if (action != null) {
                if (action == Action.POS1 || action == Action.POS2) {
                    if (args.hasAtMost(3)) {
                        return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
                    }
                } else if (action.isFillAction()) {
                    if (args.hasExactlyOne() || action == Action.REPLACE) {
                        while (args.has(2)) {
                            args.get();
                        }
                        return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
                    } else if (args.hasExactly(2)
                            && (action == Action.CYLINDER || action == Action.HCYLINDER)) {
                        args.get();
                        return args.tabCompleteDatatype(ForAxis.INSTANCE);
                    }
                } else if (action == Action.EXPAND
                        || action == Action.CONTRACT
                        || action == Action.SHIFT) {
                    if (args.hasExactlyOne()) {
                        return new TabCompleteHelper()
                                        .append(TransformTarget.getAllNames())
                                        .filterPrefix(args.getString())
                                        .sortAlphabetically()
                                        .stream();
                    } else {
                        TransformTarget target = TransformTarget.getByName(args.getString());
                        if (target != null && args.hasExactlyOne()) {
                            return args.tabCompleteDatatype(ForDirection.INSTANCE);
                        }
                    }
                }
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "WorldEdit-like commands";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The sel command allows you to manipulate Maestro's selections, similarly to"
                        + " WorldEdit.",
                "",
                "Using these selections, you can clear areas, fill them with blocks, or something"
                        + " else.",
                "",
                "The expand/contract/shift commands use a kind of selector to choose which"
                    + " selections to target. Supported ones are a/all, n/newest, and o/oldest.",
                "",
                "Usage:",
                "> sel pos1/p1/1 - Set position 1 to your current position.",
                "> sel pos1/p1/1 <x> <y> <z> - Set position 1 to a relative position.",
                "> sel pos2/p2/2 - Set position 2 to your current position.",
                "> sel pos2/p2/2 <x> <y> <z> - Set position 2 to a relative position.",
                "",
                "> sel clear/c - Clear the selection.",
                "> sel undo/u - Undo the last action (setting positions, creating selections,"
                        + " etc.)",
                "> sel set/fill/s/f [block] - Completely fill all selections with a block.",
                "> sel walls/w [block] - Fill in the walls of the selection with a specified"
                        + " block.",
                "> sel shell/shl [block] - The same as walls, but fills in a ceiling and floor"
                        + " too.",
                "> sel sphere/sph [block] - Fills the selection with a sphere bounded by the"
                        + " sides.",
                "> sel hsphere/hsph [block] - The same as sphere, but hollow.",
                "> sel cylinder/cyl [block] <axis> - Fills the selection with a cylinder bounded by"
                        + " the sides, oriented about the given axis. (default=y)",
                "> sel hcylinder/hcyl [block] <axis> - The same as cylinder, but hollow.",
                "> sel cleararea/ca - Basically 'set air'.",
                "> sel replace/r <blocks...> <with> - Replaces blocks with another block.",
                "> sel copy/cp <x> <y> <z> - Copy the selected area relative to the specified or"
                        + " your position.",
                "> sel paste/p <x> <y> <z> - Build the copied area relative to the specified or"
                        + " your position.",
                "",
                "> sel expand <target> <direction> <blocks> - Expand the targets.",
                "> sel contract <target> <direction> <blocks> - Contract the targets.",
                "> sel shift <target> <direction> <blocks> - Shift the targets (does not resize).");
    }

    enum Action {
        POS1("pos1", "p1", "1"),
        POS2("pos2", "p2", "2"),
        CLEAR("clear", "c"),
        UNDO("undo", "u"),
        SET("set", "fill", "s", "f"),
        WALLS("walls", "w"),
        SHELL("shell", "shl"),
        SPHERE("sphere", "sph"),
        HSPHERE("hsphere", "hsph"),
        CYLINDER("cylinder", "cyl"),
        HCYLINDER("hcylinder", "hcyl"),
        CLEARAREA("cleararea", "ca"),
        REPLACE("replace", "r"),
        EXPAND("expand", "ex"),
        COPY("copy", "cp"),
        PASTE("paste", "p"),
        CONTRACT("contract", "ct"),
        SHIFT("shift", "sh");
        private final ImmutableList<String> names;

        Action(String... names) {
            this.names = ImmutableList.copyOf(names);
        }

        public static Action getByName(String name) {
            for (Action action : Action.values()) {
                for (String alias : action.names) {
                    if (alias.equalsIgnoreCase(name)) {
                        return action;
                    }
                }
            }
            return null;
        }

        public static String[] getAllNames() {
            Set<String> names = new HashSet<>();
            for (Action action : Action.values()) {
                names.addAll(action.names);
            }
            return names.toArray(new String[0]);
        }

        public final boolean isFillAction() {
            return this == SET
                    || this == WALLS
                    || this == SHELL
                    || this == SPHERE
                    || this == HSPHERE
                    || this == CYLINDER
                    || this == HCYLINDER
                    || this == CLEARAREA
                    || this == REPLACE;
        }
    }

    @SuppressWarnings("ImmutableEnumChecker")
    enum TransformTarget {
        ALL(sels -> sels, "all", "a"),
        NEWEST(sels -> new Selection[] {sels[sels.length - 1]}, "newest", "n"),
        OLDEST(sels -> new Selection[] {sels[0]}, "oldest", "o");
        private final Function<Selection[], Selection[]> transform;
        private final ImmutableList<String> names;

        TransformTarget(Function<Selection[], Selection[]> transform, String... names) {
            this.transform = transform;
            this.names = ImmutableList.copyOf(names);
        }

        public Selection[] transform(Selection[] selections) {
            return transform.apply(selections);
        }

        public static TransformTarget getByName(String name) {
            for (TransformTarget target : TransformTarget.values()) {
                for (String alias : target.names) {
                    if (alias.equalsIgnoreCase(name)) {
                        return target;
                    }
                }
            }
            return null;
        }

        public static String[] getAllNames() {
            Set<String> names = new HashSet<>();
            for (TransformTarget target : TransformTarget.values()) {
                names.addAll(target.names);
            }
            return names.toArray(new String[0]);
        }
    }
}
