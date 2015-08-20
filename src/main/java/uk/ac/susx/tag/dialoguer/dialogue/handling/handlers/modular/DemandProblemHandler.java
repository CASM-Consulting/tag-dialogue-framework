package uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.modular;

import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.dialogue.components.PriorityFocus;
import uk.ac.susx.tag.dialoguer.dialogue.handling.PriorityFocusProvider;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by Saska on 7/15/2015.
 */
public class DemandProblemHandler implements Handler.ProblemHandler, PriorityFocusProvider {
    Handler.PHKey stackKey;

    public Map<String, String> demands;
    public List<String> demandChoices;

    //Slots
    public final String slot_demand = "demand";

    //Slots for outputs
    public final static String slot_out_demands = "demands";
    public final static String slot_out_demand = "demand";
    public final static String slot_out_acknowledgement = "acknowledgement";

    //Focuses
    static public final String focus_chose_demand = "chose_demand";
    static public final String focus_chose_demand_rephrase = "chose_demand_rephrase";

    //Sources
    private final String source_choices = "demand_choice";

    //Memory

    private String memloc_demand = "demand";
    private String memloc_choice = "dem_choice";

    //Demands
    public  String demand_unknown = "demand_unknown";


    /**
     * Must be called on construction of new dialogue to initialize values which will be used during execution.
     * @param d New dialouge
     */
    public void initMem(Dialogue d) {
        d.putToWorkingMemory(memloc_demand, "");
        d.putIntToWorkingMemory(memloc_choice, -1);
    }

    /**
     * Determines whether the intent set is handlable by this problem handler and returns value accordingly.
     * @param intents Current set of intent
     * @param dialogue Dialugoue instance
     * @return True if the set is handlable, false otherwise
     */
    @Override
    public boolean isInHandleableState(List<Intent> intents, Dialogue dialogue) {
        boolean demandS = intents.stream().filter(i->i.getSlots().containsKey(slot_demand)).count()>0;
        boolean demandChoice = intents.stream().filter(i->i.getSource().equals(source_choices)).count()>0;
        return (demandS || demandChoice) && dialogue.getFromWorkingMemory(memloc_demand).equals("");
    }

    /**
     * Handle the current set of intents
     * @param intents Intents extracted from user input
     * @param dialogue Dialogue instance
     * @param resource Unused
     */
    @Override
    public void handle(List<Intent> intents, Dialogue dialogue, Object resource) {
        Intent intDem = intents.stream().filter(i -> i.getSlots().containsKey(slot_demand)).findFirst().orElse(null);
        if (intDem != null && !intDem.getSlotByType(slot_demand).iterator().next().value.equals(demand_unknown)) {
            dialogue.putToWorkingMemory(memloc_demand, intDem.getSlotByType(slot_demand).iterator().next().value);
            dialogue.clearChoices();
        }
        Intent intChoice = intents.stream().filter(i -> i.getSource().equals(source_choices)).findFirst().orElse(null);
        if (intChoice != null) {
            if (intChoice.getName().equals(Intent.noChoice)) {
                dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_chose_demand_rephrase, 2));
                return;
            }


            int i = Integer.parseInt(intChoice.getSlotByType("choice").iterator().next().value);
            dialogue.putToWorkingMemory(memloc_demand, demands.entrySet()
                    .stream()
                    .filter(entry -> Objects.equals(entry.getValue(), demandChoices.get(i)))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet()).iterator().next());
        }
    }


    /**
     * Registers the key for the priority stack
     * @param key Stack key
     */
    @Override
    public void registerStackKey(Handler.PHKey key) {
        stackKey = key;
    }


    /**
     * Returns Priority Focus which is currently stored on top of the stack
     * @param d Dialouge instance
     * @return Priority focus, null if stack is empty
     */
    @Override
    public PriorityFocus peekFocus(Dialogue d) {
        if (d.multiIsEmptyFocusStack(stackKey)) {
            if (d.getFromWorkingMemory(memloc_demand).equals("")) {
                return new PriorityFocus(focus_chose_demand, 0);
            }
            return PriorityFocus.nullFocus();
        }
        int choiceMsgNo = d.getIntFromWorkingMemory(memloc_choice);
        if (d.multiPeekTopFocus(stackKey).focus.equals(focus_chose_demand_rephrase) && d.getCurrentMessageNumber() > choiceMsgNo + 1) {
            d.multiPopTopFocus(stackKey);
            return new PriorityFocus(focus_chose_demand, 0);
        }
        return d.multiPeekTopFocus(stackKey);
    }
    /**
     * Returns Priority Focus which is currently stored on top of the stack and removes it from the stack
     * @param d Dialouge instance
     * @return Priority focus, null if stack is empty
     */
    @Override
    public PriorityFocus popFocus(Dialogue d) {
        if (d.multiIsEmptyFocusStack(stackKey)) {
            if (d.getFromWorkingMemory(memloc_demand).equals("")) {
                return new PriorityFocus(focus_chose_demand, 0);
            }
            return PriorityFocus.nullFocus();
        }
        int choiceMsgNo = d.getIntFromWorkingMemory(memloc_choice);
        if (d.multiPeekTopFocus(stackKey).focus.equals(focus_chose_demand_rephrase) && d.getCurrentMessageNumber() > choiceMsgNo + 1) {
            d.multiPopTopFocus(stackKey);
            return new PriorityFocus(focus_chose_demand, 0);
        }
        return d.multiPopTopFocus(stackKey);
    }

    /**
     * Returns the user demand extracted from the conversation
     * @param d Dialogue instnace
     * @return Demand or null if not extracted
     */
    public String getDemand(Dialogue d) {
        return d.getFromWorkingMemory(memloc_demand);
    }

    /**
     * Processes the response given to the user, filling necessary information etc.
     * @param focus The response to be given
     * @param responseVariables Variables of the rseponse
     * @param d Dialogue incvzn e
     * @return Modified response variables
     */
    public Map<String, String> processResponse(String focus, Map<String, String> responseVariables, Dialogue d) {
        switch(focus) {
            case DemandProblemHandler.focus_chose_demand:
                String dmds = "";
                for (int i = 0; i < demandChoices.size(); ++i) {
                    dmds += "" + (i+1) + ") " + demandChoices.get(i);
                    if (i == demandChoices.size() - 2) {
                        dmds += " or ";
                    } else if (i < demandChoices.size() - 2) {
                        dmds += ", ";
                    }
                }
                responseVariables.put(slot_out_demands, dmds);
                d.setChoices(demandChoices);
                d.putIntToWorkingMemory(memloc_choice, d.getCurrentMessageNumber() + 1);
                break;
            case  DemandProblemHandler.focus_chose_demand_rephrase:
                if (!responseVariables.containsKey(slot_out_acknowledgement)) {
                    responseVariables.put(slot_out_acknowledgement, "");
                }
        }
        return responseVariables;
    }
}
