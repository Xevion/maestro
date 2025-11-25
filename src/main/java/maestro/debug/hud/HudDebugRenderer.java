package maestro.debug.hud;

import java.util.Map;
import java.util.Optional;
import maestro.Agent;
import maestro.api.debug.IHudDebugRenderer;
import maestro.api.pathing.calc.IPath;
import maestro.api.pathing.calc.IPathFinder;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.movement.IMovement;
import maestro.api.pathing.path.IPathExecutor;
import maestro.api.utils.BetterBlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/**
 * Renders pathfinding-focused debug information.
 *
 * <p>Displays four lines of information:
 *
 * <ul>
 *   <li>[CALC] - Pathfinding calculation metrics (nodes, time, cost)
 *   <li>[EXEC] - Movement execution state (type, progress, ETA)
 *   <li>[GOAL] - Relative position to goal (distance, direction)
 *   <li>Progress bar - Visual representation of path completion
 * </ul>
 */
public class HudDebugRenderer implements IHudDebugRenderer {

    private final Agent agent;

    // Fixed position and styling
    private static final int X = 10;
    private static final int Y = 10;
    private static final int LINE_HEIGHT = 10;
    private static final int COLOR_BACKGROUND = 0xB0000000;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    // Color coding
    private static final int COLOR_GRAY = 0xFF808080;
    private static final int COLOR_GREEN = 0xFF00FF00;
    private static final int COLOR_YELLOW = 0xFFFFFF00;
    private static final int COLOR_RED = 0xFFCC4444; // Softer red

    // Cached strings (updated per tick)
    private String calcLine = "";
    private String execLine = "";
    private String goalLine = "";
    private String progressLine = "";
    private long lastUpdateTick = -1;

    // Movement type names
    private static final Map<String, String> MOVEMENT_NAMES =
            Map.of(
                    "MovementTraverse", "Walking",
                    "MovementDiagonal", "Diagonal",
                    "MovementAscend", "Climbing",
                    "MovementDescend", "Descending",
                    "MovementPillar", "Pillaring",
                    "MovementDownward", "Ladder",
                    "MovementFall", "Falling",
                    "MovementParkour", "Parkour",
                    "MovementSwimHorizontal", "Swimming",
                    "MovementSwimVertical", "Diving");

    public HudDebugRenderer(Agent agent) {
        this.agent = agent;
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        if (!Agent.settings().debugEnabled.value) {
            return;
        }

        updateCachedStrings();

        // Collect non-empty lines
        java.util.List<LineData> lines = new java.util.ArrayList<>();
        if (!calcLine.isEmpty()) {
            lines.add(new LineData(calcLine, getCalcColor()));
        }
        if (!execLine.isEmpty()) {
            lines.add(new LineData(execLine, getExecColor()));
        }
        if (!goalLine.isEmpty()) {
            lines.add(new LineData(goalLine, getGoalColor()));
        }
        if (!progressLine.isEmpty()) {
            lines.add(new LineData(progressLine, getProgressColor()));
        }

        // If nothing to show, display idle message
        if (lines.isEmpty()) {
            lines.add(new LineData("◆ Idle", COLOR_GRAY));
        }

        // Measure maximum width
        int maxWidth = 0;
        for (LineData line : lines) {
            maxWidth = Math.max(maxWidth, getStringWidth(line.text));
        }

        // Draw background panel
        int padding = 4;
        int panelWidth = maxWidth + padding * 2;
        int panelHeight = lines.size() * LINE_HEIGHT + padding * 2;
        graphics.fill(X, Y, X + panelWidth, Y + panelHeight, COLOR_BACKGROUND);

        // Draw lines
        for (int i = 0; i < lines.size(); i++) {
            LineData line = lines.get(i);
            renderLine(graphics, i, line.text, line.color);
        }
    }

    private static class LineData {
        final String text;
        final int color;

        LineData(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    private void updateCachedStrings() {
        if (agent.getPlayerContext().world() == null) {
            return;
        }

        long currentTick = agent.getPlayerContext().world().getGameTime();
        if (currentTick == lastUpdateTick) {
            return; // Use cached values
        }
        lastUpdateTick = currentTick;

        calcLine = buildCalculationLine();
        execLine = buildExecutionLine();
        goalLine = buildGoalLine();
        progressLine = buildProgressLine();
    }

    private String buildCalculationLine() {
        Optional<? extends IPathFinder> inProgress = agent.getPathingBehavior().getInProgress();

        if (inProgress.isPresent()) {
            // Live calculation - show progress indicator
            return "◆ Calculating path...";
        } else {
            // Use cached values from current path
            IPathExecutor current = agent.getPathingBehavior().getCurrent();
            if (current != null) {
                IPath path = current.getPath();
                int nodes = path.getNumNodesConsidered();
                double cost = path.movements().stream().mapToDouble(m -> m.getCost()).sum();
                int seconds = (int) (cost / 20.0);
                return String.format("◆ %,d nodes • %d ticks (%ds)", nodes, (int) cost, seconds);
            }
        }
        return ""; // Hide when idle
    }

    private String buildExecutionLine() {
        IPathExecutor current = agent.getPathingBehavior().getCurrent();
        if (current == null) {
            return ""; // Hide when idle
        }

        IPath path = current.getPath();
        int pos = current.getPosition();
        int total = path.movements().size();

        // Bounds check
        if (pos < 0 || pos >= total) {
            return ""; // Hide when complete
        }

        String currentType = getMovementName(path.movements().get(pos));
        String nextType = pos + 1 < total ? getMovementName(path.movements().get(pos + 1)) : "Goal";

        Optional<Double> ticksRemainingOpt =
                agent.getPathingBehavior().ticksRemainingInSegment(true);
        double seconds = ticksRemainingOpt.map(ticks -> ticks / 20.0).orElse(0.0);

        return String.format(
                "▶ %s → %s • Step %d/%d • %.1fs remaining",
                currentType, nextType, pos + 1, total, seconds);
    }

    private String buildGoalLine() {
        IPathExecutor current = agent.getPathingBehavior().getCurrent();
        if (current == null) {
            return ""; // Hide when idle
        }

        Goal goal = current.getPath().getGoal();
        BetterBlockPos playerPos = agent.getPlayerContext().playerFeet();
        String relativeGoal = formatRelativeGoal(goal, playerPos);

        return String.format("⚑ %s", relativeGoal);
    }

    private String buildProgressLine() {
        IPathExecutor current = agent.getPathingBehavior().getCurrent();
        if (current == null) {
            return "";
        }

        IPath path = current.getPath();
        int pos = current.getPosition();
        int total = path.movements().size();

        if (pos < 0 || pos >= total) {
            return "[████████████████████] 100%";
        }

        // Calculate progress percentage
        double progress = (pos + 1) / (double) total;
        int percent = (int) (progress * 100);

        // Build progress bar using block characters
        int barLength = 20;
        int filled = (int) (progress * barLength);

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filled - 1) {
                bar.append("█"); // Completed
            } else if (i == filled - 1) {
                bar.append("▓"); // Current
            } else {
                bar.append("░"); // Remaining
            }
        }
        bar.append(String.format("] %d%%", percent));

        return bar.toString();
    }

    private String getMovementName(IMovement movement) {
        String className = movement.getClass().getSimpleName();
        return MOVEMENT_NAMES.getOrDefault(className, "Unknown");
    }

    private String formatRelativeGoal(Goal goal, BetterBlockPos player) {
        // Find closest goal position using heuristic
        BlockPos goalPos = findClosestGoalPosition(goal, player);

        int dx = goalPos.getX() - player.x;
        int dy = goalPos.getY() - player.y;
        int dz = goalPos.getZ() - player.z;

        // Build natural language description
        String horizontal = formatHorizontal(dx, dz);
        String vertical = formatVertical(dy);

        if (horizontal.isEmpty() && vertical.isEmpty()) {
            return "At goal";
        } else if (horizontal.isEmpty()) {
            return vertical;
        } else if (vertical.isEmpty()) {
            return horizontal;
        } else {
            return horizontal + ", " + vertical;
        }
    }

    private BlockPos findClosestGoalPosition(Goal goal, BetterBlockPos player) {
        // Use heuristic to find approximate goal position
        // Try positions in a small search space around player
        BlockPos best = new BlockPos(player.x, player.y, player.z);
        double bestHeuristic = goal.heuristic(player.x, player.y, player.z);

        // Quick search in 8 cardinal directions
        int[] offsets = {-10, 0, 10};
        for (int dx : offsets) {
            for (int dy : offsets) {
                for (int dz : offsets) {
                    int x = player.x + dx;
                    int y = player.y + dy;
                    int z = player.z + dz;

                    if (goal.isInGoal(x, y, z)) {
                        return new BlockPos(x, y, z);
                    }

                    double h = goal.heuristic(x, y, z);
                    if (h < bestHeuristic) {
                        bestHeuristic = h;
                        best = new BlockPos(x, y, z);
                    }
                }
            }
        }

        return best;
    }

    private String formatHorizontal(int dx, int dz) {
        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        if (distance == 0) {
            return "";
        }

        // Determine cardinal direction
        double angle = Math.atan2(dz, dx);
        String direction;

        if (angle < -7 * Math.PI / 8) {
            direction = "west";
        } else if (angle < -5 * Math.PI / 8) {
            direction = "northwest";
        } else if (angle < -3 * Math.PI / 8) {
            direction = "north";
        } else if (angle < -Math.PI / 8) {
            direction = "northeast";
        } else if (angle < Math.PI / 8) {
            direction = "east";
        } else if (angle < 3 * Math.PI / 8) {
            direction = "southeast";
        } else if (angle < 5 * Math.PI / 8) {
            direction = "south";
        } else if (angle < 7 * Math.PI / 8) {
            direction = "southwest";
        } else {
            direction = "west";
        }

        return String.format("%d blocks %s", distance, direction);
    }

    private String formatVertical(int dy) {
        if (dy == 0) {
            return "";
        } else if (dy > 0) {
            return String.format("%d up", dy);
        } else {
            return String.format("%d down", -dy);
        }
    }

    private void renderLine(GuiGraphics graphics, int lineIndex, String text, int color) {
        int textX = X + 4;
        int textY = Y + 4 + (lineIndex * LINE_HEIGHT);

        graphics.drawString(
                agent.getPlayerContext().minecraft().font, text, textX, textY, color, false);
    }

    private int getCalcColor() {
        Optional<? extends IPathFinder> inProgress = agent.getPathingBehavior().getInProgress();

        if (!inProgress.isPresent()) {
            // Has cached path?
            return agent.getPathingBehavior().getCurrent() != null ? COLOR_GREEN : COLOR_GRAY;
        }

        // Calculating - pulsing yellow
        long ms = System.currentTimeMillis() % 1000;
        float alpha = 0.5f + 0.5f * (float) Math.sin(ms / 159.0);
        int brightness = (int) (255 * alpha);
        return 0xFF000000 | (brightness << 16) | (brightness << 8) | 0;
    }

    private int getExecColor() {
        return COLOR_TEXT;
    }

    private int getGoalColor() {
        return COLOR_TEXT;
    }

    private int getProgressColor() {
        IPathExecutor current = agent.getPathingBehavior().getCurrent();
        if (current == null) {
            return COLOR_TEXT;
        }

        IPath path = current.getPath();
        int pos = current.getPosition();
        int total = path.movements().size();

        if (pos < 0 || pos >= total) {
            return COLOR_GREEN;
        }

        double progress = (pos + 1) / (double) total;
        if (progress > 0.5) {
            return COLOR_GREEN;
        } else if (progress > 0.25) {
            return COLOR_YELLOW;
        } else {
            return COLOR_RED;
        }
    }

    private int getStringWidth(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        var mc = agent.getPlayerContext().minecraft();
        if (mc.font != null) {
            return mc.font.width(text);
        }
        return text.length() * 6;
    }
}
