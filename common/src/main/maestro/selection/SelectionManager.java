package maestro.selection;

import java.util.ArrayList;
import java.util.ListIterator;
import maestro.Agent;
import maestro.api.utils.PackedBlockPos;
import net.minecraft.core.Direction;

public class SelectionManager {

    private final ArrayList<Selection> selections = new ArrayList<>();
    private Selection[] selectionsArr = new Selection[0];

    public SelectionManager(Agent maestro) {
        new SelectionRenderer(maestro, this);
    }

    private void resetSelectionsArr() {
        selectionsArr = selections.toArray(new Selection[0]);
    }

    public synchronized Selection addSelection(Selection selection) {
        selections.add(selection);
        resetSelectionsArr();
        return selection;
    }

    public Selection addSelection(PackedBlockPos pos1, PackedBlockPos pos2) {
        return addSelection(new Selection(pos1, pos2));
    }

    public synchronized Selection removeSelection(Selection selection) {
        selections.remove(selection);
        resetSelectionsArr();
        return selection;
    }

    public synchronized Selection[] removeAllSelections() {
        Selection[] selectionsArr = getSelections();
        selections.clear();
        resetSelectionsArr();
        return selectionsArr;
    }

    public Selection[] getSelections() {
        return selectionsArr;
    }

    public synchronized Selection getOnlySelection() {
        if (selections.size() == 1) {
            return selections.get(0);
        }

        return null;
    }

    public Selection getLastSelection() {
        if (selections.isEmpty()) return null;
        return selections.get(selections.size() - 1);
    }

    public synchronized Selection expand(Selection selection, Direction direction, int blocks) {
        for (ListIterator<Selection> it = selections.listIterator(); it.hasNext(); ) {
            Selection current = it.next();

            if (current == selection) {
                it.remove();
                it.add(current.expand(direction, blocks));
                resetSelectionsArr();
                return it.previous();
            }
        }

        return null;
    }

    public synchronized Selection contract(Selection selection, Direction direction, int blocks) {
        for (ListIterator<Selection> it = selections.listIterator(); it.hasNext(); ) {
            Selection current = it.next();

            if (current == selection) {
                it.remove();
                it.add(current.contract(direction, blocks));
                resetSelectionsArr();
                return it.previous();
            }
        }

        return null;
    }

    public synchronized Selection shift(Selection selection, Direction direction, int blocks) {
        for (ListIterator<Selection> it = selections.listIterator(); it.hasNext(); ) {
            Selection current = it.next();

            if (current == selection) {
                it.remove();
                it.add(current.shift(direction, blocks));
                resetSelectionsArr();
                return it.previous();
            }
        }

        return null;
    }
}
