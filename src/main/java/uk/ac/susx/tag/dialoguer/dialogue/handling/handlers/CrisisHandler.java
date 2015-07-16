package uk.ac.susx.tag.dialoguer.dialogue.handling.handlers;

import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.dialogue.components.Response;
import uk.ac.susx.tag.dialoguer.dialogue.handling.factories.HandlerFactory;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.modular.DemandProblemHandler;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.modular.LocationProblemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Created by Daniel Saska on 6/25/2015.
 */
public class CrisisHandler extends Handler {

    public Map<String, String> demands;
    public List<String> demandChoices;

    public static final String focus_demand_sent = "demand_sent";

    public static final String unknownResponse="unknown"; //For handeling aux. errors

    LocationProblemHandler lph = new LocationProblemHandler();
    DemandProblemHandler dph = new DemandProblemHandler();

    public CrisisHandler() {

        super.registerProblemHandler(lph);
        super.registerProblemHandler(dph);
    }

    public CrisisHandler(CrisisHandler config) {
        super.registerProblemHandler(lph);
        super.registerProblemHandler(dph);
        demands = config.demands;
        demandChoices = new ArrayList<String>(demands.values());
        dph.demandChoices = demandChoices;
        dph.demands = demands;
    }

    @Override
    public Response handle(List<Intent> intents, Dialogue dialogue) {
        if (lph.isInHandleableState(intents, dialogue)) {
            lph.handle(intents, dialogue, null);
        }
        if (dph.isInHandleableState(intents, dialogue)) {
            dph.handle(intents, dialogue, null);
        }
        return processStack(dialogue);
    }

    @Override
    public Dialogue getNewDialogue(String dialogueId) {
        Dialogue d = new Dialogue(dialogueId);
        return d;
    }

    @Override
    public HandlerFactory getFactory() {
        return null; //TODO
    }

    @Override
    public void close() throws Exception {

    }


    /****
     *
     * @param d
     * @return
     * Generate a response based on the current state of the dialogue (most specifically the FocusStack)
     * Pop the focus stack, add responseVariables which are required by this focus, generate the Response associated with this focus and responseVariables
     */
    public Response processStack(Dialogue d){
        String focus = null;
        if (!d.isEmptyFocusStack()) {
            focus = d.popTopFocus();
        } else {
            if (lph.peekFocus(d).isMoreImportantThan(dph.peekFocus(d))) {
                focus = lph.popFocus(d).focus;
            } else {
                focus = dph.popFocus(d).focus;
            }
        }
        if (focus == null) {
            focus = unknownResponse;
        }

        if (!lph.getLocation().equals("") && !dph.getDemand().equals("")) {
            focus = focus_demand_sent;
        }

        Map<String, String> responseVariables = new HashMap<>();
        dph.processResponse(focus, responseVariables, d);
        lph.processResponse(focus, responseVariables, d);
        switch(focus) {
            case focus_demand_sent:
                responseVariables.put(dph.slot_out_demand, dph.demands.get(dph.getDemand()));
                d.complete();
                break;
        }
        return new Response(focus,responseVariables);
    }
}

