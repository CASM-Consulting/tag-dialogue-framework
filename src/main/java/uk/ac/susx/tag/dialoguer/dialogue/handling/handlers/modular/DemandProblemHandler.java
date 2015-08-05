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


    public void initMem(Dialogue d) {
        d.putToWorkingMemory(memloc_demand, "");
        d.putIntToWorkingMemory(memloc_choice, -1);
    }

    @Override
    public boolean isInHandleableState(List<Intent> intents, Dialogue dialogue) {
        boolean demandS = intents.stream().filter(i->i.getSlots().containsKey(slot_demand)).count()>0;
        boolean demandChoice = intents.stream().filter(i->i.getSource().equals(source_choices)).count()>0;
        return (demandS || demandChoice) && dialogue.getFromWorkingMemory(memloc_demand).equals("");
    }

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

    @Override
    public void registerStackKey(Handler.PHKey key) {
        stackKey = key;
    }


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

    //If the handler has nothing to talk about, this method can be used to initialize new topic
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

    public String getDemand(Dialogue d) {
        return d.getFromWorkingMemory(memloc_demand);
    }

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
