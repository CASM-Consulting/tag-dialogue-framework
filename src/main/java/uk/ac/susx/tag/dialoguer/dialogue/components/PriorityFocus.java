package uk.ac.susx.tag.dialoguer.dialogue.components;

/**
 * Created by Daniel Saska on 7/15/2015.
 */
public class PriorityFocus {
    public PriorityFocus() {
        this.priority = 0;
        this.focus = "";
    }
    public PriorityFocus(String focus) {
        this.priority = 0;
        this.focus = focus;
    }
    public PriorityFocus(String focus, int priority) {
        this.priority = priority;
        this.focus = focus;
    }

    public String focus;
    public int priority;

    public boolean isMoreImportantThan(PriorityFocus other) {
        return priority > other.priority;
    }

    public static PriorityFocus nullFocus() {
        return  new PriorityFocus(null, Integer.MIN_VALUE);
    }
}
