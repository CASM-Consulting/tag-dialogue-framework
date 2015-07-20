package uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.modular;

import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;

import java.util.List;
import java.util.Map;

/**
 * Created by Daniel Saska on 7/20/2015.
 */
public class IdkProblemHandler implements Handler.ProblemHandler {
    Map<String, String> helpTable;

    @Override
    public boolean isInHandleableState(List<Intent> intents, Dialogue dialogue) {
        boolean demandS = intents.stream().filter(i->i.getSlots().containsKey("")).count()>0;
        return demandS;
    }

    @Override
    public void handle(List<Intent> intents, Dialogue dialogue, Object resource) {
    }
}

