package uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.modular;

import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.dialogue.components.PriorityFocus;
import uk.ac.susx.tag.dialoguer.dialogue.handling.PriorityFocusProvider;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;
import uk.ac.susx.tag.dialoguer.knowledge.location.NominatimAPIWrapper;
import uk.ac.susx.tag.dialoguer.knowledge.location.RadiusAssigner;

import java.util.*;

/**
 * Created by Saska on 7/15/2015.
 */
public class LocationProblemHandler implements Handler.ProblemHandler, PriorityFocusProvider {
    Handler.PHKey stackKey;

    NominatimAPIWrapper nom = new NominatimAPIWrapper();

    private static final int maxDiamter = 200;


    //Focuses
    static public final String focus_enable_gps = "enable_gps";
    static public final String focus_location = "location";
    static public final String focus_location_confirm = "location_confirm";
    static public final String focus_recheck_geoloc = "recheck_geoloc";
    static public final String focus_address_not_found = "address_not_found";
    static public final String focus_landmarks = "landmarks";
    static public final String focus_landmarks_different = "landmarks_different";
    static public final String focus_landmarks_more = "landmarks_more";
    static public final String focus_landmarks_combo_problem = "landmarks_combo_problem";
    static public final String focus_landmarks_cleared = "landmarks_cleared";


    //Intents
    private  final String intent_no_gps = "no_gps";

    //Sources
    private final String source_confirm = "confirm_location";
    private final String source_ood = "out_of_domain_no_gps";
    private final String source_landmarks= "landmarks";

    //Slots
    public final String slot_location = "location";
    public final String slot_landmark = "landmark";
    public final String slot_place = "place"; //TODO: Same nameawrr

    //Slots for outputs
    public final static String slot_out_location = "location";
    public final static String slot_out_landmark = "landmark";
    public final static String slot_out_acknowledgement = "acknowledgement";
    public static final String slot_out_num_locations = "n_loc";

    //Memory of the location handler. We will hold here whatever the user has been doing so we don't offer him the same
    //options over and over again.

    private Progress gpsProgress = Progress.notStarted;
    private boolean hasGps = true; //True by default, let user state that he does not have it.

    private String memloc_location = "loc_location";
    private String memloc_locationGiven = "loc_locationGiven";
    private String memloc_locationLat = "loc_lat";
    private String memloc_locationLon = "loc_lon";
    private String memloc_locationSource = "loc_source";
    private String memloc_locationHasLatLon = "loc_haslatlon";
    private String memloc_locationConfirmed = "loc_confirmed";
    private String memloc_locationsPossible = "loc_possible";
    private String memloc_landmarks = "loc_landm";
    private String memloc_landmarksAskedMsgNo = "loc_asked";

    private String memloc_acknowledgeChance = "loc_ack";

    private String memloc_landmarkBad = "loc_bad";

    enum Progress {
        notStarted,
        inProgress,
        done
    }

    class LocationSource {
        public static final int Landmarks = 0;
        public static final int Gps = 1;
        public static final int Location = 2;
    }

    public void initMem(Dialogue d) {
        d.putToWorkingMemory(memloc_location, "");
        d.putToWorkingMemory(memloc_locationGiven, "");
        d.putDoubleToWorkingMemory(memloc_locationLat, 0.0);
        d.putDoubleToWorkingMemory(memloc_locationLon, 0.0);
        d.putIntToWorkingMemory(memloc_locationSource, -1);
        d.putBooleanToWorkingMemory(memloc_locationHasLatLon, false);
        d.putBooleanToWorkingMemory(memloc_locationConfirmed, false);
        d.putIntToWorkingMemory(memloc_locationsPossible, 1);
        d.putListToWorkingMemory(memloc_landmarks, new ArrayList<>());
        d.putIntToWorkingMemory(memloc_landmarksAskedMsgNo, -1);

        d.putBooleanToWorkingMemory(memloc_acknowledgeChance, false);

        d.putToWorkingMemory(memloc_landmarkBad, "");
    }

    @Override
    public boolean isInHandleableState(List<Intent> intents, Dialogue dialogue) {
        boolean no_gps = intents.stream().filter(i->i.getName().equals(intent_no_gps)).count()>0;
        boolean loc = intents.stream().filter(i->i.getSlots().containsKey(slot_location)).count()>0;
        boolean landmark = intents.stream().filter(i->i.getSlots().containsKey(slot_landmark)).count()>0;
        landmark |= intents.stream().filter(i->i.getSlots().containsKey(slot_place)).count()>0;
        boolean confirm = intents.stream().filter(i->i.getName().equals(source_confirm)).count()>0;
        return loc || (((landmark | no_gps | gpsProgress != Progress.done) || (!dialogue.getStringFromWorkingMemory(memloc_location).equals("") && confirm)) && !dialogue.getBooleanFromWorkingMemory(memloc_locationConfirmed));
        //If we didn't try gps yet then lets there is chance to use that.
        //If we have confirmed location, we do not need to handle the location anymore.
    }

    @Override
    public void handle(List<Intent> intents, Dialogue dialogue, Object resource) {
        Intent intLoc = intents.stream().filter(i -> i.getSlots().containsKey(slot_location)).findFirst().orElse(null);
        Intent intLm = null;
        Intent.Slot slLm = null;
        if (dialogue.getCurrentMessageNumber() == dialogue.getIntFromWorkingMemory(memloc_landmarksAskedMsgNo) + 1) {
            intLm = intents.stream().filter(i -> i.getSource().equals(source_landmarks)).filter(i -> i.getSlots().containsKey(slot_place)).findFirst().orElse(null);
            slLm = (intLm == null) ? null : intLm.getSlotByType(slot_place).iterator().next();
        } else {
            intLm = intents.stream().filter(i -> i.getSlots().containsKey(slot_landmark)).findFirst().orElse(null);
            slLm = (intLm == null) ? null : intLm.getSlotByType(slot_landmark).iterator().next(); //TODO: Is it necessary to handle multiple or is one enough?
        }
        Intent.Slot slLoc = (intLoc == null) ? null : intLoc.getSlotByType(slot_location).iterator().next(); //TODO: Is it necessary to handle multiple or is one enough?

        boolean is_ood = intents.stream().filter(i -> i.getSource().equals(source_ood)).filter(i -> i.getName().equals("out_of_domain")).findFirst().orElse(null) != null;
        if (!is_ood && intents.stream().filter(i -> i.getName().equals(intent_no_gps)).findFirst().orElse(null) != null) {
            hasGps = false;
            if (dialogue.getIntFromWorkingMemory(memloc_locationSource) == LocationSource.Gps) {
                dialogue.putBooleanToWorkingMemory(memloc_locationConfirmed, false);
                dialogue.putToWorkingMemory(memloc_location, "");
                dialogue.putToWorkingMemory(memloc_locationGiven, "");
                dialogue.putBooleanToWorkingMemory(memloc_locationHasLatLon, false);
                dialogue.putDoubleToWorkingMemory(memloc_locationLat, 0.0);
                dialogue.putDoubleToWorkingMemory(memloc_locationLon, 0.0);
                dialogue.putIntToWorkingMemory(memloc_locationsPossible, 1);
                dialogue.putIntToWorkingMemory(memloc_locationSource, -1);
            }
        }

        if (slLm != null && dialogue.getCurrentMessageNumber() == dialogue.getIntFromWorkingMemory(memloc_landmarksAskedMsgNo) + 1) {
            Intent intConfirm = intents.stream().filter(i -> i.getSource().equals(source_confirm)).findFirst().orElse(null);
            if (intConfirm.getName().equals("no")) {
                dialogue.pushFocus(new PriorityFocus(focus_location_confirm, 3));
                return;
            }
            if (!validateLandmark(dialogue, slLm.value)) {
                dialogue.putToWorkingMemory(memloc_landmarkBad, slLm.value);
                dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_landmarks_different, 3));
                return;
            }
            List<String> landmarks = dialogue.getListFromWorkingMemory(memloc_landmarks);
            landmarks.add(slLm.value);

            List<NominatimAPIWrapper.NomResult> instances[] = new List[landmarks.size()];
            for (int i = 0; i < landmarks.size(); ++i) {
                NominatimAPIWrapper.NomResult results[] = nom.queryAPI(landmarks.get(i) + ", " + dialogue.getStringFromWorkingMemory(memloc_location), 200, 0, 1);
                instances[i] = Arrays.asList(results);
            }
            List<List<NominatimAPIWrapper.NomResult>> areas = new ArrayList<>();
            buildAreas(0, instances, areas);
            dialogue.putIntToWorkingMemory(memloc_locationsPossible, areas.size());
            int possible = dialogue.getIntFromWorkingMemory(memloc_locationsPossible);
            if (dialogue.getIntFromWorkingMemory(memloc_locationsPossible) > 1) {
                dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_landmarks_more, 2));
            } else if (dialogue.getIntFromWorkingMemory(memloc_locationsPossible) == 1) {
                NominatimAPIWrapper.NomResult res = areas.get(0).get(areas.get(0).size() - 1);
                String location = "";
                if (!res.address.keySet().iterator().next().equals("road")
                        && !res.address.keySet().iterator().next().equals("house_number")
                        && !res.address.keySet().iterator().next().equals("footway") ) {
                    location += res.address.values().iterator().next() + ", ";
                }
                if (res.address.get("house_number") != null) {
                    location +=  res.address.get("house_number") + ", ";
                }
                if (res.address.get("road") != null) {
                    location += res.address.get("road") + ", ";
                } else if (res.address.get("footway") != null) {
                    location += res.address.get("footway") + ", ";
                }
                if (res.address.get("city") != null) {
                    location += res.address.get("city") + ", ";
                } else if (res.address.get("town") != null) {
                    location += res.address.get("town") + ", ";
                } else if (res.address.get("village") != null) {
                    location += res.address.get("village") + ", ";
                }
                location += res.address.get("county");
                if (res.address.get("postcode") != null) {
                    location += ", " + res.address.get("postcode");
                }
                dialogue.putToWorkingMemory(memloc_location, location);
                dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_location_confirm, 3));
            } else if (dialogue.getIntFromWorkingMemory(memloc_locationsPossible) == 0) {
                dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_landmarks_combo_problem, 5));
            }

            return;
        }

        if (slLoc != null) {
            List<String> landmarks = dialogue.getListFromWorkingMemory(memloc_landmarks);
            landmarks.clear();
            if (slLm != null) {
                landmarks.add(slLm.value);
            }
            dialogue.setRequestingYesNo(false);

            NominatimAPIWrapper.NomResult[] res = nom.queryAPI(slLoc.value, 1, 0, 1);
            dialogue.putToWorkingMemory(memloc_locationGiven, slLoc.value);
            if (res.length > 0) {
                if (gpsProgress == Progress.inProgress) {
                    gpsProgress = Progress.done;
                }
                String location = "";
                if (!res[0].address.keySet().iterator().next().equals("road")
                        && !res[0].address.keySet().iterator().next().equals("house_number")
                        && !res[0].address.keySet().iterator().next().equals("footway") ) {
                    location += res[0].address.values().iterator().next() + ", ";
                }
                if (res[0].address.get("house_number") != null) {
                    location += res[0].address.get("house_number") + ", ";
                }
                if (res[0].address.get("road") != null) {
                    location += res[0].address.get("road") + ", ";
                } else if (res[0].address.get("footway") != null) {
                    location += res[0].address.get("footway") + ", ";
                }
                if (res[0].address.get("city") != null) {
                    location += res[0].address.get("city") + ", ";
                } else if (res[0].address.get("town") != null) {
                    location += res[0].address.get("town") + ", ";
                }
                location += res[0].address.get("county");
                if (res[0].address.get("postcode") != null) {
                    location += ", " + res[0].address.get("postcode");
                }
                dialogue.putToWorkingMemory(memloc_location, location);
                dialogue.putBooleanToWorkingMemory(memloc_locationHasLatLon, false);
                dialogue.putDoubleToWorkingMemory(memloc_locationLat, 0.0);
                dialogue.putDoubleToWorkingMemory(memloc_locationLon, 0.0);
                dialogue.putIntToWorkingMemory(memloc_locationsPossible, 1);
                dialogue.putIntToWorkingMemory(memloc_locationSource, LocationSource.Location);
                dialogue.putBooleanToWorkingMemory(memloc_locationConfirmed, false); //Location does not need confirmation when manually entered //TODO: is this good idea?
                dialogue.putBooleanToWorkingMemory(memloc_acknowledgeChance, true);

                if (!res[0].address.containsKey("house_number")) {
                    dialogue.putIntToWorkingMemory(memloc_locationsPossible, 500);
                    if (landmarks.size() > 0) {
                        if (!validateLandmark(dialogue, landmarks.get(0))) {
                            dialogue.putToWorkingMemory(memloc_landmarkBad, landmarks.get(0));
                            landmarks.remove(0);
                            dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_landmarks_different, 3));
                            return;
                        }
                        List<NominatimAPIWrapper.NomResult> instances[] = new List[landmarks.size()];
                        for (int i = 0; i < landmarks.size(); ++i) {
                            NominatimAPIWrapper.NomResult results[] = nom.queryAPI(landmarks.get(i) + ", " + location, 200, 0, 1);
                            instances[i] = Arrays.asList(results);
                        }
                        List<List<NominatimAPIWrapper.NomResult>> areas = new ArrayList<>();
                        buildAreas(0, instances, areas);
                        dialogue.putIntToWorkingMemory(memloc_locationsPossible, areas.size());
                        if (dialogue.getIntFromWorkingMemory(memloc_locationsPossible) > 1) {
                            dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_landmarks, 2));
                        } else {
                            dialogue.putBooleanToWorkingMemory(memloc_locationConfirmed, true);
                        }
                        return;
                    }
                    dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_landmarks, 2));
                } else {
                    dialogue.putBooleanToWorkingMemory(memloc_locationConfirmed, true);
                }
                return;
            } else {
                dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_address_not_found, 5));
                return;
            }
        }


        //We have location candidate so its ok to expect confirmation
        Intent intConfirm = intents.stream().filter(i -> i.getSource().equals(source_confirm)).findFirst().orElse(null);
        if (! dialogue.getStringFromWorkingMemory(memloc_location).equals("") && intConfirm != null &&  dialogue.getIntFromWorkingMemory(memloc_locationsPossible) == 0) {
            if(intConfirm.getName().equals("yes")) {
                dialogue.getListFromWorkingMemory(memloc_landmarks).clear();
                dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_landmarks_cleared, 2));
                dialogue.setRequestingYesNo(false);
                return; //We are done
            } else if(intConfirm.getName().equals("no")) {
                dialogue.putBooleanToWorkingMemory(memloc_locationConfirmed, false);
                //Ditch the old stuff so it does not get messy.
                dialogue.putToWorkingMemory(memloc_location, "");
                dialogue.putToWorkingMemory(memloc_locationGiven, "");
                dialogue.putBooleanToWorkingMemory(memloc_locationHasLatLon, false);
                dialogue.putDoubleToWorkingMemory(memloc_locationLat, 0.0);
                dialogue.putDoubleToWorkingMemory(memloc_locationLon, 0.0);
                dialogue.putIntToWorkingMemory(memloc_locationsPossible, 1);
                dialogue.putIntToWorkingMemory(memloc_locationSource, -1);
                dialogue.setRequestingYesNo(false);
            }
        }

        if (! dialogue.getStringFromWorkingMemory(memloc_location).equals("") && intConfirm != null) {
            if(intConfirm.getName().equals("yes")) {
                dialogue.putBooleanToWorkingMemory(memloc_locationConfirmed, true);
                dialogue.setRequestingYesNo(false);
                return; //We are done
            } else if(intConfirm.getName().equals("no")) {
                dialogue.putBooleanToWorkingMemory(memloc_locationConfirmed, false);
                //Ditch the old stuff so it does not get messy.
                dialogue.putToWorkingMemory(memloc_location, "");
                dialogue.putToWorkingMemory(memloc_locationGiven, "");
                dialogue.putBooleanToWorkingMemory(memloc_locationHasLatLon, false);
                dialogue.putDoubleToWorkingMemory(memloc_locationLat, 0.0);
                dialogue.putDoubleToWorkingMemory(memloc_locationLon, 0.0);
                dialogue.putIntToWorkingMemory(memloc_locationsPossible, 1);
                dialogue.putIntToWorkingMemory(memloc_locationSource, -1);
                dialogue.setRequestingYesNo(false);
            }
        }


        if (gpsProgress != Progress.done && hasGps) {
            if (gpsProgress == Progress.inProgress) {
                //Tell user to double check that he shares accurate location. If we get response with location intent we will use that
                dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_recheck_geoloc, 3));
                gpsProgress = Progress.notStarted;
                return;
            } else if (dialogue.getUserData().isLocationDataPresent()) { //Bingo, we have GPS location, confirm it with the user.

                //Its fine to ditch old address if necessary
                dialogue.putToWorkingMemory(memloc_location, "");
                dialogue.putToWorkingMemory(memloc_locationGiven, "");
                dialogue.putBooleanToWorkingMemory(memloc_locationHasLatLon, true);
                dialogue.putDoubleToWorkingMemory(memloc_locationLat, dialogue.getUserData().getLatitude());
                dialogue.putDoubleToWorkingMemory(memloc_locationLon, dialogue.getUserData().getLongitude());

                NominatimAPIWrapper.NomResult loc = nom.queryReverseAPI(dialogue.getDoubleFromWorkingMemory(memloc_locationLat), dialogue.getDoubleFromWorkingMemory(memloc_locationLon), 18, 1);

                String location = "";
                if (!loc.address.keySet().iterator().next().equals("road")
                        && !loc.address.keySet().iterator().next().equals("house_number")
                        && !loc.address.keySet().iterator().next().equals("footway") ) {
                    location += loc.address.values().iterator().next() + ", ";
                }
                if (loc.address.get("house_number") != null) {
                    location += loc.address.get("house_number") + ", ";
                }
                if (loc.address.get("road") != null) {
                    location += loc.address.get("road") + ", ";
                } else if (loc.address.get("footway") != null) {
                    location += loc.address.get("footway") + ", ";
                }
                if (loc.address.get("city") != null) {
                    location += loc.address.get("city") + ", ";
                } else if (loc.address.get("town") != null) {
                    location += loc.address.get("town") + ", ";
                } else if (loc.address.get("village") != null) {
                    location += loc.address.get("village") + ", ";
                }
                location += loc.address.get("county") + ", ";
                location += loc.address.get("postcode");
                dialogue.putToWorkingMemory(memloc_location, location);
                dialogue.putIntToWorkingMemory(memloc_locationSource, LocationSource.Gps);
                dialogue.putIntToWorkingMemory(memloc_locationsPossible, 1);
                //We want the location to be confirmed, we will for now ask the main Handler to do this for us but this may be [TODO]
                dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_location_confirm, 3));
                if (gpsProgress == Progress.inProgress) {
                    gpsProgress = Progress.done;
                } else {
                    gpsProgress = Progress.inProgress;
                }
                return;
            } else { //We don't have geo tag
                dialogue.multiPushFocus(stackKey, new PriorityFocus(focus_enable_gps, 2));
            }
        }
    }

    @Override
    public void registerStackKey(Handler.PHKey key) {
        stackKey = key;
    }

    @Override
    public PriorityFocus peekFocus(Dialogue d) {
        if (d.multiIsEmptyFocusStack(stackKey)) {
            if ( d.getStringFromWorkingMemory(memloc_location).equals("")) {
                return  new PriorityFocus(focus_location, 1);
            }
            return PriorityFocus.nullFocus();
        }
        return d.multiPeekTopFocus(stackKey);
    }

    //If the handler has nothing to talk about, this method can be used to initialize new topic
    @Override
    public PriorityFocus popFocus(Dialogue d) {
        if (d.multiIsEmptyFocusStack(stackKey)) {
            if ( d.getStringFromWorkingMemory(memloc_location).equals("")) {
                return  new PriorityFocus(focus_location, 1);
            }
            return PriorityFocus.nullFocus();
        }
        return d.multiPopTopFocus(stackKey);
    }

    public Map<String, String> processResponse(String focus, Map<String, String> responseVariables, Dialogue d) {
        switch(focus) {
            case focus_location_confirm:
                responseVariables.put(slot_out_location,  d.getStringFromWorkingMemory(memloc_location));
                break;
            case focus_landmarks_more:
                responseVariables.put(slot_out_num_locations, Integer.toString(d.getIntFromWorkingMemory(memloc_locationsPossible)));
            case focus_landmarks_cleared:
            case focus_landmarks:
                d.putIntToWorkingMemory(memloc_landmarksAskedMsgNo, d.getCurrentMessageNumber() + 1);
                d.setRequestingYesNo(true);
                break;
            case focus_landmarks_different:
                responseVariables.put(slot_out_landmark, d.getStringFromWorkingMemory(memloc_landmarkBad));
                d.putIntToWorkingMemory(memloc_landmarksAskedMsgNo, d.getCurrentMessageNumber() + 1);
                d.setRequestingYesNo(true);
                break;
            case focus_landmarks_combo_problem:
                d.setRequestingYesNo(true);

        }
        if (d.getBooleanFromWorkingMemory(memloc_acknowledgeChance)) {
            d.putBooleanToWorkingMemory(memloc_acknowledgeChance, false);
            responseVariables.put(slot_out_acknowledgement, "Got it. "); //TODO: set in json
        }

        return responseVariables;
    }

    public String getLocation(Dialogue d) {
        if (!d.getBooleanFromWorkingMemory(memloc_locationConfirmed)) {
            return "";
        }
        return  d.getStringFromWorkingMemory(memloc_location);
    }

    public List<String> getLandmarks(Dialogue d) {
        return d.getListFromWorkingMemory(memloc_landmarks);
    }

    public boolean validateLandmark(Dialogue d, String landmark) {
        NominatimAPIWrapper.NomResult quick_results[] = nom.queryAPI(landmark + ", " +  d.getStringFromWorkingMemory(memloc_locationGiven), 1, 0, 0);
        if (quick_results.length == 0) {
            return false;
        }
        return true;
    }

    private void buildAreas(int lmark, List<NominatimAPIWrapper.NomResult> instances[], List<List<NominatimAPIWrapper.NomResult>> areas) {
        if (lmark == 0) {
            for (int i = 0; i < instances[lmark].size(); ++i) {
                List<NominatimAPIWrapper.NomResult> area = new ArrayList<>();
                area.add(instances[lmark].get(i));
                areas.add(area);
            }
        } else {
            List<List<NominatimAPIWrapper.NomResult>> areasNew = new ArrayList<>();
            for (int k = 0; k < instances[lmark].size(); ++k) {
                for (int i = 0; i < areas.size(); ++i) {
                    List<NominatimAPIWrapper.NomResult> area = new ArrayList<>();
                    area.add(instances[lmark].get(k));
                    for (int j = 0; j < areas.get(i).size(); ++j) {
                        double dist = RadiusAssigner.haversineM(Double.parseDouble(instances[lmark].get(k).lat)
                                , Double.parseDouble(instances[lmark].get(k).lon)
                                , Double.parseDouble(areas.get(i).get(j).lat)
                                , Double.parseDouble(areas.get(i).get(j).lon));
                        if (dist > maxDiamter) {
                            break;
                        }
                        area.add(areas.get(i).get(j));
                    }
                    if (area.size() == lmark + 1) {
                        areasNew.add(area);
                    }
                }
            }
            areas.clear();
            areas.addAll(areasNew);
        }
        if (lmark < instances.length - 1) {
            buildAreas(lmark + 1, instances, areas);
        }
    }
}
