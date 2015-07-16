package uk.ac.susx.tag.dialoguer.dialogue.handling;

import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.PriorityFocus;

/**
 * Created by Daniel Saska on 7/15/2015.
 */
public interface PriorityFocusProvider {
    public PriorityFocus peekFocus(Dialogue d);
    public PriorityFocus popFocus(Dialogue d);
}
