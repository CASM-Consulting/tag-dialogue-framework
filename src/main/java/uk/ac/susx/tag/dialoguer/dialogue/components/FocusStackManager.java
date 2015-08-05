package uk.ac.susx.tag.dialoguer.dialogue.components;

import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;

import java.util.*;

/**
 * Created by Daniel Saska on 7/28/2015.
 */
public class FocusStackManager {
    private Map<Handler.PHKey, List<PriorityFocus>> focusStacks;

    public FocusStackManager () {
        focusStacks = new HashMap<>();
        focusStacks.put(new Handler.PHKey(""), new ArrayList<>()); //Main focus stack
    }

    public List<PriorityFocus> getFocusStack() {
        return  focusStacks.get(new Handler.PHKey(""));
    }

    /**
     * Returns or create-returns (if necessary) stack with key provided
     * @return
     */
    public List<PriorityFocus> getFocusStack(Handler.PHKey stackKey) {
        if (!focusStacks.containsKey(stackKey)) {
            focusStacks.put(stackKey, new Stack<>());
        }
        return focusStacks.get(stackKey);
    }

    /**
     * Removes focus stack identified by key provided. Main stack ("") cannot be removed.
     * @param stackKey Key of the stack to be removed
     * @return True if stack could be remvoed, False if it could not (unexisting, attempt on main focus stack, ...)
     */
    public boolean removeFocusStack(Handler.PHKey stackKey) {
       if (stackKey.uid.equals("") || !focusStacks.containsKey(stackKey)) {
           return false;
       }
        focusStacks.remove(stackKey);
        return  true;
    }

    /**
     * Removes all auxiliary stacks and clears main focus stack
     */
    public void clear() {
        focusStacks.clear();
        focusStacks.put(new Handler.PHKey(""), new Stack<>()); //Main focus stack
    }

    /***********************************************
     * Question focus stack
     ***********************************************/

    public String peekTopFocus() { return getFocusStack().get(getFocusStack().size() - 1).focus;}
    public String popTopFocus() { return getFocusStack().remove(getFocusStack().size() - 1).focus;}
    public void pushFocus(PriorityFocus newTopFocus) { getFocusStack().add(newTopFocus); }
    public boolean isFocusPresent(String focus) { return getFocusStack().contains(new PriorityFocus(focus)); }
    public boolean isEmptyFocusStack() {return getFocusStack().isEmpty();}
    public void removeFocus(String focus) { getFocusStack().remove(focus); }
    public void clearFocusStack() { getFocusStack().clear();}

    /***********************************************
     * Separte Question focus stacks
     ***********************************************/

    public PriorityFocus multiPeekTopFocus(Handler.PHKey stackKey) { return getFocusStack(stackKey) == null ? null : ((getFocusStack(stackKey).size() == 0) ? null : getFocusStack(stackKey).get(getFocusStack(stackKey).size() - 1));}
    public PriorityFocus multiPopTopFocus(Handler.PHKey stackKey) { return getFocusStack(stackKey) == null ? null : ((getFocusStack(stackKey).size() == 0) ? null : getFocusStack(stackKey).remove(getFocusStack(stackKey).size() - 1));}
    public void multiPushFocus(Handler.PHKey stackKey, PriorityFocus newTopFocus) { if (getFocusStack(stackKey) != null) { getFocusStack(stackKey).add(newTopFocus); }; }
    public boolean multiIsFocusPresent(Handler.PHKey stackKey, String focus) { return getFocusStack(stackKey) == null ? false : getFocusStack(stackKey).contains(new PriorityFocus(focus)); }
    public void multiRemoveFocus(Handler.PHKey stackKey, String focus) { if (getFocusStack(stackKey) != null) { getFocusStack(stackKey).remove(focus); } }
    public boolean multiIsEmptyFocusStack(Handler.PHKey stackKey) {return getFocusStack(stackKey) == null ? true :  getFocusStack(stackKey).isEmpty(); }
    public void multiClearFocusStack(Handler.PHKey stackKey) { if (getFocusStack(stackKey) != null) { getFocusStack(stackKey).clear(); } }
    public void multiRemoveFocusStack(Handler.PHKey stackKey) { if (getFocusStack(stackKey) != null) { focusStacks.remove(stackKey); } }
}
