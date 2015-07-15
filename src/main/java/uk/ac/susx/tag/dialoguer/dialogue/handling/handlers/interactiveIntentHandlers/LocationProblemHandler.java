package uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.interactiveIntentHandlers;

import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.InteractiveHandler;
import uk.ac.susx.tag.dialoguer.knowledge.location.NominatimAPIWrapper;

import java.util.Collection;
import java.util.List;

/**
 * Created by Daniel Saska on 6/26/2015.
 */
public class LocationProblemHandler implements Handler.ProblemHandler {
    NominatimAPIWrapper nom = new NominatimAPIWrapper();

    @Override
    public boolean isInHandleableState(List<Intent> intents, Dialogue dialogue) {
        if (dialogue.isEmptyFocusStack()) { return false; }
        boolean intentmatch = intents.stream().filter(i->i.getName().equals(InteractiveHandler.locationIntent)).count()>0;
        boolean intentmatch2 = intents.stream().filter(i->i.getName().equals(InteractiveHandler.locationUnknownIntent)).count()>0;
        boolean statematch = dialogue.peekTopFocus().equals(InteractiveHandler.aLocation);
        return (intentmatch || intentmatch2) && statematch;
    }

    @Override
    public void handle(List<Intent> intents, Dialogue dialogue, Object resource) {
        System.err.println("location intent handler fired");
        Intent intent = intents.stream().filter(i->i.isName(InteractiveHandler.locationIntent)).findFirst().orElse(null);
        if (intent != null) {
            Collection<Intent.Slot> location = intent.getSlotByType(InteractiveHandler.locationSlot);
            String locationStr = location.iterator().next().value;
            dialogue.putToWorkingMemory("location_given", locationStr + ", United Kingdom");

            NominatimAPIWrapper.NomResult results[] = nom.queryAPI(locationStr + ", United Kingdom", 100, 0, 1);//For now assume we are in UK

            if (results.length == 0) {
                dialogue.pushFocus(InteractiveHandler.qLocationAgain);
                return;
            }
            //TODO: Find better way to identify ambiguity
            int ambiguous = 0;
            for (NominatimAPIWrapper.NomResult nr : results) {
                if (nr.type == "city") {
                    ++ambiguous;
                }
            }

            //TODO: Handle this better than taking straight first result
            NominatimAPIWrapper.NomResult loc = results[0];
            String addressName = "";
            if (!loc.address.keySet().iterator().next().equals("road")
                    && !loc.address.keySet().iterator().next().equals("house_number")
                    && !loc.address.keySet().iterator().next().equals("footway") ) {
                addressName += loc.address.values().iterator().next() + ", ";
            }
            if (loc.address.get("house_number") != null) {
                addressName += loc.address.get("house_number") + ", ";
            }
            if (loc.address.get("road") != null) {
                addressName += loc.address.get("road") + ", ";
            } else if (loc.address.get("footway") != null) {
                addressName += loc.address.get("footway") + ", ";
            }
            if (loc.address.get("city") != null) {
                addressName += loc.address.get("city") + ", ";
            } else if (loc.address.get("town") != null) {
                addressName += loc.address.get("town") + ", ";
            }
            addressName += loc.address.get("county") + ", ";
            addressName += loc.address.get("postcode");
            dialogue.putToWorkingMemory("location_processed", addressName);
            dialogue.putToWorkingMemory(InteractiveHandler.addressConfirmFlag, "");
            if (!loc.address.containsKey("house_number")) {
                dialogue.pushFocus(InteractiveHandler.aLandmarks); //Quickly stating landmark might be easier if we need only house number
                dialogue.pushFocus(InteractiveHandler.qLandmarks);
                return;
            }

            ((InteractiveHandler)resource).finalizeRequest(dialogue);
        }
        else {
            dialogue.appendToWorkingMemory("location_processed", "United Kingdom");
            intent = intents.stream().filter(i->i.isName(InteractiveHandler.locationUnknownIntent)).findFirst().orElse(null);
            //GPS is MUST if you dont know the city
            dialogue.pushFocus(InteractiveHandler.aLandmarks);
            dialogue.pushFocus(InteractiveHandler.qLandmarks);
            dialogue.pushFocus(InteractiveHandler.aEnableGps);
            dialogue.pushFocus(InteractiveHandler.qEnableGps);
        }

    }
}
