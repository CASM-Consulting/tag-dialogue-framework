package uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.interactiveIntentHandlers;

import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.dialogue.components.Message;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.InteractiveHandler;
import uk.ac.susx.tag.dialoguer.knowledge.location.NominatimAPIWrapper;

import java.util.List;

/**
 * Created by Daniel Saska on 6/28/2015.
 */
public class GpsProblemHandler implements Handler.ProblemHandler {
    NominatimAPIWrapper nom = new NominatimAPIWrapper();

    @Override
    public boolean isInHandleableState(List<Intent> intents, Dialogue dialogue) {
        return dialogue.isEmptyFocusStack()||dialogue.peekTopFocus().equals(InteractiveHandler.aWaitGps);
    }

    @Override
    public void handle(List<Intent> intents, Dialogue dialogue, Object resource) {
        InteractiveHandler.handleGps(dialogue);
    }

    @Override
    public void registerStackKey(Handler.PHKey key) {

    }
}

