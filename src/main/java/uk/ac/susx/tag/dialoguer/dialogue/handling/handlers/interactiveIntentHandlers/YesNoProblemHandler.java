package uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.interactiveIntentHandlers;

import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.InteractiveHandler;

import java.util.List;

/**
 * Created by Daniel Saska on 6/26/2015.
 */
public class YesNoProblemHandler implements Handler.ProblemHandler {
    @Override
    public boolean isInHandleableState(List<Intent> intents, Dialogue dialogue) {
        if (dialogue.isEmptyFocusStack()) { return false; }

        boolean intentmatch = intents.stream().anyMatch(i-> i.isName(InteractiveHandler.yesNoIntent));
        intentmatch |= intents.stream().anyMatch(i-> i.isName("yes"));
        intentmatch |= intents.stream().anyMatch(i-> i.isName("no"));


        boolean statematch1 = dialogue.peekTopFocus().equals(InteractiveHandler.aEnableGps);
        boolean statematch2 = dialogue.peekTopFocus().equals(InteractiveHandler.aGpsHelp);
        boolean statematch4 = dialogue.peekTopFocus().equals(InteractiveHandler.aLocationConfirm);
        boolean statematch5 = dialogue.peekTopFocus().equals(InteractiveHandler.aGpsLocConfirm);
        return  intentmatch && (statematch1 || statematch2 || statematch4 || statematch5);
    }

    @Override
    public void handle(List<Intent> intents, Dialogue dialogue, Object resource) {
        System.err.println("Yes-No");

        Intent intent = intents.stream().filter(i->i.isName(InteractiveHandler.yesNoIntent)).findFirst().orElse(null);
        if (intent == null) {
            intent = intents.stream().filter(i->i.isName("yes")).findFirst().orElse(null);
        }
        if (intent == null) {
            intent = intents.stream().filter(i->i.isName("no")).findFirst().orElse(null);
        }


        if (dialogue.peekTopFocus().equals(InteractiveHandler.aEnableGps)) {
            if(intent.getName().equals("yes")) {
                dialogue.pushFocus(InteractiveHandler.aWaitGps);
                dialogue.pushFocus(InteractiveHandler.qWaitGps);
            }
            else if(intent.getName().equals("no")) {
                dialogue.pushFocus(InteractiveHandler.aGpsHelp);
                dialogue.pushFocus(InteractiveHandler.qGpsHelp);
            }
        } else if (dialogue.peekTopFocus().equals(InteractiveHandler.aGpsHelp)) {
            if(intent.getName().equals("yes")) {
                dialogue.pushFocus(InteractiveHandler.helpGps);
                //TODO: Continue this branch
            }
            else if(intent.getName().equals("no")) {
                dialogue.pushFocus(InteractiveHandler.aLandmarks);
                dialogue.pushFocus(InteractiveHandler.qLandmarks);
            }

        }  else if (dialogue.peekTopFocus().equals(InteractiveHandler.aLocationConfirm)) {
            if (dialogue.getFromWorkingMemory(InteractiveHandler.addressConfirmFlag).equals("Y")) {
                dialogue.pushFocus(InteractiveHandler.demandSent);
                ((InteractiveHandler)resource).finalizeRequest(dialogue);
                return;
            }
            if(intent.getName().equals("yes")) {
                dialogue.putToWorkingMemory(InteractiveHandler.addressConfirmFlag, "Y");
                dialogue.pushFocus(InteractiveHandler.demandSent);
            } else if(intent.getName().equals("no")) {
                dialogue.clearWorkingIntents();
                dialogue.pushFocus(InteractiveHandler.aLocation);
                dialogue.pushFocus(InteractiveHandler.qLocation);
            }
        } else if (dialogue.peekTopFocus().equals(InteractiveHandler.aGpsLocConfirm)) {
            if(intent.getName().equals("yes")) {
                dialogue.putToWorkingMemory(InteractiveHandler.addressConfirmFlag, "Y");
                ((InteractiveHandler)resource).finalizeRequest(dialogue);
            } else if(intent.getName().equals("no")) {
                dialogue.pushFocus(InteractiveHandler.aLocation);
                dialogue.pushFocus(InteractiveHandler.qLocation);
                dialogue.removeFromWorkingMemory("location_processed");
                dialogue.removeFromWorkingMemory(InteractiveHandler.addressConfirmFlag);
            }
        }
    }
}
