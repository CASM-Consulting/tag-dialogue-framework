package uk.ac.susx.tag.dialoguer.dialogue.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Created by Daniel Saska on 7/28/2015.
 */
public class FocusStackManager {
    private Map<Integer, List<PriorityFocus>> focusStacks;
    int nextId;

    public FocusStackManager () {
        focusStacks.put(0, new ArrayList<>()); //Main focus stack
        nextId = 1;
    }

    public List<PriorityFocus> getFocusStack() {
        return  focusStacks.get(0);
    }

    public List<PriorityFocus> getFocusStack(int id) {
        if (!focusStacks.containsKey(id)) {
            return null;
        }
        return  focusStacks.get(id);
    }

    public int createFocusStack() {
        focusStacks.put(nextId, new Stack<>());
        return nextId++;
    }

    /**
     * Removes focus stack identified by ID provided. Main stack (0) cannot be removed.
     * @param id ID of the stack to be removed
     * @return True if stack could be remvoed, False if it could not (unexisting, attempt on main focus stack, ...)
     */
    public boolean removeFocusStack(int id) {
       if (id == 0 || !focusStacks.containsKey(id)) {
           return false;
       }
        focusStacks.remove(id);
        return  true;
    }

    /**
     * Removes all auxiliary stacks and clears main focus stack
     */
    public void clear() {
        focusStacks.clear();
        focusStacks.put(0, new Stack<>()); //Main focus stack
        nextId = 1;
    }

    /***********************************************
     * Question focus stack
     ***********************************************/

    public String peekTopFocus() { return getFocusStack().get(getFocusStack().size() - 1).focus;}
    public String popTopFocus() { return getFocusStack().remove(getFocusStack().size() - 1).focus;}
    public void pushFocus(String newTopFocus) { getFocusStack().add(new PriorityFocus(newTopFocus)); }
    public boolean isFocusPresent(String focus) { return getFocusStack().contains(new PriorityFocus(focus)); }
    public boolean isEmptyFocusStack() {return getFocusStack().isEmpty();}
    public void removeFocus(String focus) { getFocusStack().remove(focus); }
    public void clearFocusStack() { getFocusStack().clear();}

    /***********************************************
     * Separte Question focus stacks
     ***********************************************/

    public String multiPeekTopFocus(int id) { return getFocusStack(id) == null ? null : getFocusStack(id).get(getFocusStack().size() - 1).focus;}
    public String multiPopTopFocus(int id) { return getFocusStack(id) == null ? null : getFocusStack(id).remove(getFocusStack().size() - 1).focus;}
    public void multiPushFocus(int id, String newTopFocus) { if (getFocusStack(id) != null) { getFocusStack(id).add(new PriorityFocus(newTopFocus)); }; }
    public boolean multiIsFocusPresent(int id, String focus) { return getFocusStack(id) == null ? false : getFocusStack(id).contains(new PriorityFocus(focus)); }
    public void multiRemoveFocus(int id, String focus) { if (getFocusStack(id) != null) { getFocusStack(id).remove(focus); } }
    public boolean multiIsEmptyFocusStack(int id) {return getFocusStack(id) == null ? true :  getFocusStack(id).isEmpty(); }
    public void multiClearFocusStack(int id) { if (getFocusStack(id) != null) { getFocusStack(id).clear(); } }
    public void multiRemoveFocusStack(int id) { if (getFocusStack(id) != null) { focusStacks.remove(id); } }
}
