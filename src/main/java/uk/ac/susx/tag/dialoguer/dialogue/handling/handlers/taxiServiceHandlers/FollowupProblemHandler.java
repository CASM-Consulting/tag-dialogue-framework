package uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.taxiServiceHandlers;

import com.google.common.collect.Sets;
import uk.ac.susx.tag.dialoguer.Dialoguer;
import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.ProductSearchHandler;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.TaxiServiceHandler;

import java.util.List;
import java.util.stream.Collectors;

/**
 * What to do when the user is following up on a previous orderTaxi intent
 * Created by juliewe on 19/05/2015.
 */


public class FollowupProblemHandler implements Handler.ProblemHandler {

    /**
     *
     * @param intents
     * @param dialogue
     * @return
     * Check that there is at least one followup intent in the list of intents and that intent has at least one slot filled
     */
    @Override
    public boolean isInHandleableState(List<Intent> intents, Dialogue dialogue) {
        Intent intent= intents.stream().filter(i-> TaxiServiceHandler.followupIntents.contains(i.getName())).findFirst().orElse(null);
        if(intent==null){
            return false;
        } else {
            return intent.isAnySlotFilled();
        }
    }

    /**
     *
     * @param intents
     * @param dialogue
     * @param resource
     * @return
     *
     * First determine whether the user was accepting or rejecting previously given information
     * Add the orderTaxiIntent (which should also be present if we are in this state) to the workingIntents and then proceed on the followupIntent based on its name.
     * Handle the slot that is being followed up on according to the context
     * Return true as this problemHandler has fired and updated the stack accordingly.
     */
    @Override
    public void handle(List<Intent> intents, Dialogue dialogue, Object resource) {
        System.err.println("Followup Problem Handler fired");
        int accepting = determineAccepting(intents);
        Intent followup = intents.stream().filter(i->TaxiServiceHandler.followupIntents.contains(i.getName())).findFirst().orElse(null); // will not be null as otherwise not inHandleableState
        dialogue.addToWorkingIntents(intents.stream().filter(i->i.isName(TaxiServiceHandler.orderTaxiIntent)).collect(Collectors.toList())); //save any orderTaxiIntents to working intents
        if(dialogue.isEmptyWorkingIntents()){ // this should not happen because this intents require the "followup" state to be set
            throw new Dialoguer.DialoguerException("Follow up intent generated when no orderTaxiIntents present");
        }
        switch (followup.getName()){
            case TaxiServiceHandler.followupCapacityIntent:
                handleEntity(followup,dialogue,accepting, TaxiServiceHandler.capacitySlot);
                break;
            case TaxiServiceHandler.followupTimeIntent:
                handleEntity(followup,dialogue,accepting, TaxiServiceHandler.timeSlot);
                break;
            case TaxiServiceHandler.followupLocationIntent:
                if(followup.areSlotsFilled(Sets.newHashSet(TaxiServiceHandler.destinationSlot))) {
                    handleEntity(followup, dialogue, accepting, TaxiServiceHandler.destinationSlot);
                }
                if(followup.areSlotsFilled(Sets.newHashSet(TaxiServiceHandler.pickupSlot))){
                        handleEntity(followup,dialogue,accepting, TaxiServiceHandler.pickupSlot);
                    }

                break;
            case TaxiServiceHandler.followupNegativeIntent:
                rejectEntity(followup,dialogue,accepting);

        }
    }

    @Override
    public void registerStackKey(Handler.PHKey key) {

    }

    /**
     *
     * @param intents
     * @return 1, -1, 0
     * Look for yes/no intents generated by the yesNoAnalyser
     * 1=yes
     * -1=no
     * 0 = none
     */
    private static int determineAccepting(List<Intent> intents){
        int res=0;
        Intent confirmation = Intent.getFirstIntentFromSource(ProductSearchHandler.yesNoAnalyser, intents);
        if(confirmation!=null) {
            if (confirmation.isName(Intent.yes)) {
                res = 1;
            } else {
                if (confirmation.isName(Intent.no)) {
                    res = -1;
                }
            }
        }
        return res;
    }

    /***
     *
     * @param i
     * @param d
     * @param accepting
     * @param slotname
     * validate the values in the slot for the followup intent
     * Handle the update according to whether it was accepting or rejecting
     */
    static void handleEntity(Intent i, Dialogue d, int accepting, String slotname){
        List<String> values = TaxiServiceHandler.validate(i,slotname);
        TaxiServiceHandler.update(accepting,values,slotname,d);
    }

    static void rejectEntity(Intent i, Dialogue d, int accepting){
        String slotname=TaxiServiceHandler.allSlots.stream().filter(name->i.areSlotsFilled(Sets.newHashSet(name))).findFirst().orElse(null);
        String rejectedvalue=i.getSlotValuesByType(slotname).get(0);
        //String currentvalue=d.peekTopIntent().getSlotValuesByType(slotname).stream().filter(value->value.equals(rejectedvalue)).findFirst().orElse(null);
        List<String> othervalues=d.peekTopIntent().getSlotValuesByType(slotname).stream().filter(value->!value.equals(rejectedvalue)).collect(Collectors.toList());
        if(othervalues.size()<d.peekTopIntent().getSlotValuesByType(slotname).size()){
            d.peekTopIntent().clearSlots(slotname);
            d.peekTopIntent().fillSlots(othervalues.stream().map(value->new Intent.Slot(slotname,value,0,0)).collect(Collectors.toList()));

        }
        if(othervalues.isEmpty()){
            d.pushFocus(TaxiServiceHandler.respecifyResponse);
        }
        if(othervalues.size()>1){
            d.pushFocus(TaxiServiceHandler.chooseResponse);
        }
        d.putToWorkingMemory("slot_to_choose",slotname);
        if(d.peekTopFocus().equals(TaxiServiceHandler.confirmCompletionResponse)){
            d.pushFocus(TaxiServiceHandler.confirmResponse);
        }
    }

}
