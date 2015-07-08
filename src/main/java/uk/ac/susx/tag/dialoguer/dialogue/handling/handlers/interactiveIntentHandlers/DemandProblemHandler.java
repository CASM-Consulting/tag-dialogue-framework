package uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.interactiveIntentHandlers;

import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.dialogue.components.Message;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.InteractiveHandler;
import uk.ac.susx.tag.dialoguer.knowledge.location.NominatimAPIWrapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Daniel Saska on 7/3/2015.
 */
public class DemandProblemHandler {

    public boolean isInHandleableState(List<Intent> intents, Dialogue dialogue, InteractiveHandler ih) {
        String demand = "";
        if (dialogue.getFromWorkingMemory(InteractiveHandler.demandFlag) != null) {
            demand = dialogue.getFromWorkingMemory(InteractiveHandler.demandFlag);
        }
        if (!demand.equals("")) {
            return false;
        }
        boolean ret = intents.stream().anyMatch(i-> ih.demands.containsKey(i.getName()));
        ret |= dialogue.peekTopFocus().equals(InteractiveHandler.aWhatHelp) && intents.stream().filter(i -> i.getSource().equals(InteractiveHandler.choiceIntent)).filter(i->i.getName().equals(Intent.choice)).count()>0;
        return ret;
    }

    public void handle(List<Intent> intents, Dialogue dialogue, Object resource) {
        InteractiveHandler ih = (InteractiveHandler) resource;
        if (dialogue.peekTopFocus().equals(InteractiveHandler.aWhatHelp)) {
            Intent intent = intents.stream().filter(i -> i.getSource().equals(InteractiveHandler.choiceIntent)).filter(i->i.isName(Intent.nullChoice)).findFirst().orElse(null);
            if (intent != null) {
                dialogue.putToWorkingMemory(InteractiveHandler.demandFlag, InteractiveHandler.demNothing);
            }
            intent = intents.stream().filter(i -> i.getSource().equals(InteractiveHandler.choiceIntent)).filter(i->i.isName(Intent.choice)).findFirst().orElse(null);
            if (intent != null) {
                int i = Integer.parseInt(intent.getSlotByType("choice").iterator().next().value);
                String key = ih.demands.entrySet()
                        .stream()
                        .filter(entry -> Objects.equals(entry.getValue(), ih.demandChoices.get(i)))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet()).iterator().next();
                dialogue.putToWorkingMemory(InteractiveHandler.demandFlag, key);
            }

        }
        Intent intent = intents.stream().filter(i ->  ih.demands.containsKey(i.getName())).findFirst().orElse(null);
        if (intent != null) {
            dialogue.putToWorkingMemory(InteractiveHandler.demandFlag, intent.getName());
        }
    }
}
