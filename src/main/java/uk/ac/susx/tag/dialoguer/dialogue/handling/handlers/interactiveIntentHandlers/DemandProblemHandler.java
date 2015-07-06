package uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.interactiveIntentHandlers;

import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.dialogue.components.Message;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.InteractiveHandler;
import uk.ac.susx.tag.dialoguer.knowledge.location.NominatimAPIWrapper;

import java.util.List;

/**
 * Created by Daniel Saska on 7/3/2015.
 */
public class DemandProblemHandler implements Handler.ProblemHandler {

    @Override
    public boolean isInHandleableState(List<Intent> intents, Dialogue dialogue) {
        String demand = "";
        if (dialogue.getFromWorkingMemory(InteractiveHandler.demandFlag) != null) {
            demand = dialogue.getFromWorkingMemory(InteractiveHandler.demandFlag);
        }
        if (!demand.equals("")) {
            return false;
        }
        boolean ret = intents.stream().anyMatch(i-> i.isName(InteractiveHandler.demFiredepIntent));
        ret |= intents.stream().anyMatch(i-> i.isName(InteractiveHandler.demMedicalIntent));
        ret |= intents.stream().anyMatch(i-> i.isName(InteractiveHandler.demPoliceIntent));
        ret |= intents.stream().anyMatch(i-> i.isName(InteractiveHandler.demUnknownIntent));
        return ret;
    }

    @Override
    public void handle(List<Intent> intents, Dialogue dialogue, Object resource) {
        if (dialogue.peekTopFocus().equals(InteractiveHandler.aWhatHelp)) {
            Intent intent = intents.stream().filter(i -> i.getSource().equals(InteractiveHandler.choiceIntent)).filter(i->i.isName(Intent.nullChoice)).findFirst().orElse(null);
            if (intent != null) {
                dialogue.putToWorkingMemory(InteractiveHandler.demandFlag, InteractiveHandler.demNothing);
            }
            intent = intents.stream().filter(i -> i.getSource().equals(InteractiveHandler.choiceIntent)).filter(i->i.isName(Intent.choice)).findFirst().orElse(null);
            if (intent != null) {
                if (intent.getSlotByType("choice").iterator().next().equals("0")) {
                    dialogue.putToWorkingMemory(InteractiveHandler.demandFlag, InteractiveHandler.demFiredepIntent);
                }
                if (intent.getSlotByType("choice").iterator().next().equals("1")) {
                    dialogue.putToWorkingMemory(InteractiveHandler.demandFlag, InteractiveHandler.demMedicalIntent);
                }
                if (intent.getSlotByType("choice").iterator().next().equals("2")) {
                    dialogue.putToWorkingMemory(InteractiveHandler.demandFlag, InteractiveHandler.demPoliceIntent);
                }
            }

        }

        if (intents.stream().anyMatch(i-> i.isName(InteractiveHandler.demFiredepIntent))) {
            dialogue.putToWorkingMemory(InteractiveHandler.demandFlag, InteractiveHandler.demFiredepIntent);
        }
        if (intents.stream().anyMatch(i-> i.isName(InteractiveHandler.demMedicalIntent))) {
            dialogue.putToWorkingMemory(InteractiveHandler.demandFlag, InteractiveHandler.demMedicalIntent);
        }
        if (intents.stream().anyMatch(i-> i.isName(InteractiveHandler.demPoliceIntent))) {
            dialogue.putToWorkingMemory(InteractiveHandler.demandFlag, InteractiveHandler.demPoliceIntent);
        }
        if (intents.stream().anyMatch(i -> i.isName(InteractiveHandler.demUnknownIntent))) {
            dialogue.putToWorkingMemory(InteractiveHandler.demandFlag, InteractiveHandler.demUnknownIntent);
        }
    }
}
