package uk.ac.susx.tag.dialoguer.dialogue.handling.handlers;

import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.dialogue.components.Message;
import uk.ac.susx.tag.dialoguer.dialogue.components.Response;
import uk.ac.susx.tag.dialoguer.dialogue.handling.factories.HandlerFactory;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.interactiveIntentHandlers.*;
import uk.ac.susx.tag.dialoguer.knowledge.location.NominatimAPIWrapper;

import java.util.*;

/**
 * Created by Daniel Saska on 6/25/2015.
 */
public class InteractiveHandler extends Handler {
    private Map<String,String> knowledge = new HashMap<>();


    private Map<String, String> humanReadableSlotNames; //read from config file

    private DemandProblemHandler demandProblemHandler = new DemandProblemHandler();
    static private NominatimAPIWrapper nom = new NominatimAPIWrapper();


    //Intent names
    public static final String unknownIntent="unknown";
    public static final String locationIntent="location";
    public static final String locationUnknownIntent="unknown_location";
    public static final String yesNoIntent="yes_no";
    public static final String landmarkIntent = "landmark";
    public static final String helpIntent = "help";
    public static final String multichocieIntent = "simple_multichoice";
    public static final String choiceIntent = "simple_choice";
    //public static final String demFiredepIntent = "demand_firedep";
    //public static final String demMedicalIntent = "demand_medical";
    //public static final String demPoliceIntent = "demand_police";
    public static final String demUnknownIntent = "";
    public static final String demNothing = "demand_nothing";

    //Response/focus/state names
    public static final String initial = "initial";
    public static final String apology = "apology";
    public static final String qLocation = "q_location";
    public static final String qLocationAgain = "q_location_again";
    public static final String aLocation = "a_location";
    public static final String unknownResponse="unknown"; //For handeling aux. errors
    public static final String confirmResponse="confirm"; //Asks user to confirm his choice
    public static final String qEnableGps="q_enable_gps"; //Asks user whether he can enable gps
    public static final String aEnableGps="a_enable_gps";
    public static final String aWaitGps ="a_wait_gps";
    public static final String qWaitGps ="q_wait_gps";
    public static final String helpGps="help_gps";
    public static final String qGpsHelp="q_need_help_gps"; //Asks user whether he wants help with tunring on the gps
    public static final String aGpsHelp="a_need_help_gps";
    public static final String qGpsLocConfirm="q_confirm_gps_location"; //Asks user whether he wants help with tunring on the gps
    public static final String aGpsLocConfirm="a_confirm_gps_location";
    public static final String qWhatHelp ="q_what_help"; //Asks user whether he needs abulance called
    public static final String aWhatHelp ="q_what_help";
    public static final String qLandmarks="q_landmarks";//Asks user whether he can see any landmarks such as KFC or other points of interest
    public static final String aLandmarks="a_landmarks";
    public static final String aLeaveLandmark="a_leave_landmark";
    public static final String qLeaveLandmark="q_leave_landmark";
    public static final String qLandmarksRemove="q_remove_landmark";
    public static final String qChoiceRephrase="q_choice_rephrase";
    public static final String landmarkNotFound="no_landmark_found";//NO instances of such landmark were found
    public static final String qAddLandmarks="q_add_landmarks";//Asks user for more landmarks
    public static final String qLocationConfirm="q_location_confirm";//Asks user to confirm location for very last time
    public static final String aLocationConfirm="a_location_confirm";
    public static final String demandSent="demand_sent"; //Inform user about help being dispatched.

    //Slot names
    public static final String locationSlot="location";
    public static final String landmarkSlot="landmark";
    public static final String landmarksSlot="landmarks";
    public static final String demandSlot="demand";
    public static final String demandsSlot="demands";
    public static final String yesNoSlot="yes_no";
    public static final String addressSlot="address";
    public static final String nLocationsSlot = "n_loc";

    //Flags
    public static final String demandFlag = "demand_flag";
    public static final String addressConfirmFlag = "address_confirm_flag";

    public Map<String, String> demands;
    public List<String> demandChoices;

    public InteractiveHandler() {

        super.registerProblemHandler(new UnknownProblemHandler());
        super.registerProblemHandler(new YesNoProblemHandler());
        super.registerProblemHandler(new LocationProblemHandler());
        super.registerProblemHandler(new HelpProblemHelper());
        super.registerProblemHandler(new LandmarkProblemHandler());
        super.registerProblemHandler(new GpsProblemHandler());
    }

    public InteractiveHandler(InteractiveHandler config) {
        super.registerProblemHandler(new UnknownProblemHandler());
        super.registerProblemHandler(new YesNoProblemHandler());
        super.registerProblemHandler(new LocationProblemHandler());
        super.registerProblemHandler(new HelpProblemHelper());
        super.registerProblemHandler(new LandmarkProblemHandler());
        super.registerProblemHandler(new GpsProblemHandler());
        demands = config.demands;
        demandChoices = new ArrayList<String>(demands.values());
    }

    @Override
    public Response handle(List<Intent> intents, Dialogue dialogue) {
        boolean complete=useFirstProblemHandler(intents, dialogue, this); //is there a problem handler?
        if (demandProblemHandler.isInHandleableState(intents, dialogue, this)) {
            demandProblemHandler.handle(intents, dialogue, this);
            complete = true;
        }
        if(!complete){ //no problem handler or intent handler
            dialogue.pushFocus(unknownResponse);
        }
        return processStack(dialogue);
    }

    @Override
    public Dialogue getNewDialogue(String dialogueId) {
        Dialogue d = new Dialogue(dialogueId);
        d.setState("initial");
        return d;
    }

    @Override
    public HandlerFactory getFactory() {
        return null;
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
        String focus=unknownResponse;
        if (!d.isEmptyFocusStack()) {
            focus = d.popTopFocus();
        }

        //If we have all data we can ignore whatever was happening and finalize the dialog
        String address = "";
        String addressConfirm = "";
        String demand = "";
        if (d.getFromWorkingMemory("location_processed") != null) {
            address = d.getFromWorkingMemory("location_processed");
        }
        if (d.getFromWorkingMemory(addressConfirmFlag) != null) {
            addressConfirm = d.getFromWorkingMemory(addressConfirmFlag);
        }
        if (d.getFromWorkingMemory(demandFlag) != null) {
            demand = d.getFromWorkingMemory(demandFlag);
        }
        System.err.println("FLAGS");
        System.err.println(" | Address='"+address+"'");
        System.err.println(" | AddressConfirm='"+addressConfirm+"'");
        System.err.println(" | Demand='"+demand+"'");

        if (!address.equals("") && !addressConfirm.equals("") && !demand.equals("")) {
            focus = demandSent;
        }


        Map<String, String> responseVariables = new HashMap<>();
        switch(focus) {
            case qGpsLocConfirm:
                responseVariables.put(addressSlot, d.getFromWorkingMemory("location_processed"));
                break;
            case qAddLandmarks:
                responseVariables.put(nLocationsSlot, d.getFromWorkingMemory("n_loc"));
                break;
            case qLandmarksRemove:
                responseVariables.put(landmarksSlot, d.getFromWorkingMemory("landmarks"));
                break;
            case qLocationConfirm:
                responseVariables.put(addressSlot, d.getFromWorkingMemory("location_processed"));
            case demandSent:
                responseVariables.put(demandSlot, demands.get(d.getFromWorkingMemory(demandFlag)));
                break;
            case qWhatHelp:
                String dmds = "";
                for (int i = 0; i < demandChoices.size(); ++i) {
                    dmds += "" + (i+1) + ") " + demandChoices.get(i);
                    if (i == demandChoices.size() - 2) {
                        dmds += " or ";
                    } else if (i < demandChoices.size() - 2) {
                        dmds += ", ";
                    }
                }
                responseVariables.put(demandsSlot, dmds);
                break;

        }
        return new Response("q_location_again",responseVariables);

    }

    public void finalizeRequest(Dialogue dialogue) {

        String addressConfirm = "";
        String demand = "";
        if (dialogue.getFromWorkingMemory(addressConfirmFlag) != null) {
            addressConfirm = dialogue.getFromWorkingMemory(addressConfirmFlag);
        }
        if (dialogue.getFromWorkingMemory(demandFlag) != null) {
            demand = dialogue.getFromWorkingMemory(demandFlag);
        }

        if (demand.equals("")) {
            dialogue.pushFocus(InteractiveHandler.aWhatHelp);
            dialogue.pushFocus(InteractiveHandler.qWhatHelp);
            dialogue.setChoices(demandChoices);
        }
        if (addressConfirm.equals("")) {
            dialogue.pushFocus(InteractiveHandler.aLocationConfirm);
            dialogue.pushFocus(InteractiveHandler.qLocationConfirm);
        }
    }

    public static void handleGps(Dialogue dialogue) {
        if (!dialogue.getUserData().isLocationDataPresent()) {
            dialogue.pushFocus(InteractiveHandler.aLocation);
            dialogue.pushFocus(InteractiveHandler.qLocation);
            return;
        }

        double lat = dialogue.getUserData().getLatitude();
        double lon = dialogue.getUserData().getLongitude();


        NominatimAPIWrapper.NomResult loc = nom.queryReverseAPI(lat, lon);
        dialogue.putToWorkingMemory("location_processed", loc.display_name);
        dialogue.putToWorkingMemory(InteractiveHandler.addressConfirmFlag, "");

        dialogue.pushFocus(InteractiveHandler.aGpsLocConfirm);
        dialogue.pushFocus(InteractiveHandler.qGpsLocConfirm);
    }
}
