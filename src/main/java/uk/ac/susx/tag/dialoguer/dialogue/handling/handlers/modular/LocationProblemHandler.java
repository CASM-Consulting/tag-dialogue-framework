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
    NominatimAPIWrapper nom = new NominatimAPIWrapper();
    Stack<PriorityFocus> localFocusStack = new Stack<>();

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

    private String location = "";
    private String locationGiven = "";
    private double locationLat = 0.0;
    private double locationLon = 0.0;
    private LocationSource locationSource = null;
    private boolean locationHasLatLon = false;
    private boolean locationConfirmed = false;
    private int locationsPossible = 1;
    private List<String> landmarks = new ArrayList<>();
    private int landmarksAskedMsgNo = -1;

    private boolean acknowledgeChance = false;

    private String landmarkBad = "";

    enum Progress {
        notStarted,
        inProgress,
        done
    }

    enum LocationSource {
        Landmarks,
        Gps,
        Location
    }

    @Override
    public boolean isInHandleableState(List<Intent> intents, Dialogue dialogue) {
        boolean no_gps = intents.stream().filter(i->i.getName().equals(intent_no_gps)).count()>0;
        boolean loc = intents.stream().filter(i->i.getSlots().containsKey(slot_location)).count()>0;
        boolean landmark = intents.stream().filter(i->i.getSlots().containsKey(slot_landmark)).count()>0;
        landmark |= intents.stream().filter(i->i.getSlots().containsKey(slot_place)).count()>0;
        boolean confirm = intents.stream().filter(i->i.getName().equals(source_confirm)).count()>0;
        return loc || (((landmark | no_gps | gpsProgress != Progress.done) || (!location.equals("") && confirm)) && !locationConfirmed);
        //If we didn't try gps yet then lets there is chance to use that.
        //If we have confirmed location, we do not need to handle the location anymore.
    }

    @Override
    public void handle(List<Intent> intents, Dialogue dialogue, Object resource) {
        Intent intLoc = intents.stream().filter(i -> i.getSlots().containsKey(slot_location)).findFirst().orElse(null);
        Intent intLm = null;
        Intent.Slot slLm = null;
        if (dialogue.getCurrentMessageNumber() == landmarksAskedMsgNo + 1) {
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
            if (locationSource == LocationSource.Gps) {
                locationConfirmed = false;
                location = "";
                locationGiven = "";
                locationHasLatLon = false;
                locationLat = 0.0;
                locationLon = 0.0;
                locationsPossible = 1;
                locationSource = null;
            }
        }

        if (slLm != null && dialogue.getCurrentMessageNumber() == landmarksAskedMsgNo + 1) {
            Intent intConfirm = intents.stream().filter(i -> i.getSource().equals(source_confirm)).findFirst().orElse(null);
            if (intConfirm.getName().equals("no")) {
                localFocusStack.push(new PriorityFocus(focus_location_confirm, 3));
                return;
            }
            if (!validateLandmark(slLm.value)) {
                landmarkBad = slLm.value;
                localFocusStack.add(new PriorityFocus(focus_landmarks_different, 3));
                return;
            }
            landmarks.add(slLm.value);

            List<NominatimAPIWrapper.NomResult> instances[] = new List[landmarks.size()];
            for (int i = 0; i < landmarks.size(); ++i) {
                NominatimAPIWrapper.NomResult results[] = nom.queryAPI(landmarks.get(i) + ", " + location, 200, 0, 1);
                instances[i] = Arrays.asList(results);
            }
            List<List<NominatimAPIWrapper.NomResult>> areas = new ArrayList<>();
            buildAreas(0, instances, areas);
            locationsPossible = areas.size();
            if (locationsPossible > 1) {
                localFocusStack.add(new PriorityFocus(focus_landmarks_more, 2));
            } else if (locationsPossible == 1) {
                NominatimAPIWrapper.NomResult res = areas.get(0).get(areas.get(0).size() - 1);
                location = "";
                if (!res.address.keySet().iterator().next().equals("road")
                        && !res.address.keySet().iterator().next().equals("house_number")
                        && !res.address.keySet().iterator().next().equals("footway") ) {
                    location += res.address.values().iterator().next() + ", ";
                }
                if (res.address.get("house_number") != null) {
                    location += res.address.get("house_number") + ", ";
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
                localFocusStack.push(new PriorityFocus(focus_location_confirm, 3));
            } else if (locationsPossible == 0) {
                localFocusStack.add(new PriorityFocus(focus_landmarks_combo_problem, 5));
            }

            return;
        }

        if (slLoc != null) {
            landmarks.clear();
            if (slLm != null) {
                landmarks.add(slLm.value);
            }
            dialogue.setRequestingYesNo(false);

            NominatimAPIWrapper.NomResult[] res = nom.queryAPI(slLoc.value, 1, 0, 1);
            locationGiven = slLoc.value;
            if (res.length > 0) {
                if (gpsProgress == Progress.inProgress) {
                    gpsProgress = Progress.done;
                }
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
                locationHasLatLon = false;
                locationLat = 0.0;
                locationLon = 0.0;
                locationsPossible = 1;
                locationSource = LocationSource.Location;
                locationConfirmed = false; //Location does not need confirmation when manually entered //TODO: is this good idea?
                acknowledgeChance = true;

                if (!res[0].address.containsKey("house_number")) {
                    locationsPossible = 500;
                    if (landmarks.size() > 0) {
                        if (!validateLandmark(landmarks.get(0))) {
                            landmarkBad = landmarks.get(0);
                            landmarks.remove(0);
                            localFocusStack.add(new PriorityFocus(focus_landmarks_different, 3));
                            return;
                        }
                        List<NominatimAPIWrapper.NomResult> instances[] = new List[landmarks.size()];
                        for (int i = 0; i < landmarks.size(); ++i) {
                            NominatimAPIWrapper.NomResult results[] = nom.queryAPI(landmarks.get(i) + ", " + location, 200, 0, 1);
                            instances[i] = Arrays.asList(results);
                        }
                        List<List<NominatimAPIWrapper.NomResult>> areas = new ArrayList<>();
                        buildAreas(0, instances, areas);
                        locationsPossible = areas.size();
                        if (locationsPossible > 1) {
                            localFocusStack.add(new PriorityFocus(focus_landmarks, 2));
                        } else {
                            locationConfirmed = true;
                        }
                        return;
                    }
                    localFocusStack.add(new PriorityFocus(focus_landmarks, 2));
                } else {
                    locationConfirmed = true;
                }
                return;
            } else {
                localFocusStack.add(new PriorityFocus(focus_address_not_found, 5));
                return;
            }
        }


        //We have location candidate so its ok to expect confirmation
        Intent intConfirm = intents.stream().filter(i -> i.getSource().equals(source_confirm)).findFirst().orElse(null);
        if (!location.equals("") && intConfirm != null && locationsPossible == 0) {
            if(intConfirm.getName().equals("yes")) {
                landmarks.clear();
                localFocusStack.add(new PriorityFocus(focus_landmarks_cleared, 2));
                dialogue.setRequestingYesNo(false);
                return; //We are done
            } else if(intConfirm.getName().equals("no")) {
                locationConfirmed = false;
                //Ditch the old stuff so it does not get messy.
                location = "";
                locationGiven = "";
                locationHasLatLon = false;
                locationLat = 0.0;
                locationLon = 0.0;
                locationsPossible = 1;
                locationSource = null;
                dialogue.setRequestingYesNo(false);
            }
        }

        if (!location.equals("") && intConfirm != null) {
            if(intConfirm.getName().equals("yes")) {
                locationConfirmed = true;
                dialogue.setRequestingYesNo(false);
                return; //We are done
            } else if(intConfirm.getName().equals("no")) {
                locationConfirmed = false;
                //Ditch the old stuff so it does not get messy.
                location = "";
                locationGiven = "";
                locationHasLatLon = false;
                locationLat = 0.0;
                locationLon = 0.0;
                locationsPossible = 1;
                locationSource = null;
                dialogue.setRequestingYesNo(false);
            }
        }


        if (gpsProgress != Progress.done && hasGps) {
            if (gpsProgress == Progress.inProgress) {
                //Tell user to double check that he shares accurate location. If we get response with location intent we will use that
                localFocusStack.push(new PriorityFocus(focus_recheck_geoloc, 3));
                gpsProgress = Progress.notStarted;
                return;
            } else if (dialogue.getUserData().isLocationDataPresent()) { //Bingo, we have GPS location, confirm it with the user.

                //Its fine to ditch old address if necessary
                location = "";
                locationGiven = "";
                locationHasLatLon = true;
                locationLat = dialogue.getUserData().getLatitude();
                locationLon = dialogue.getUserData().getLongitude();

                NominatimAPIWrapper.NomResult loc = nom.queryReverseAPI(locationLat, locationLon, 18, 1);

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
                locationSource = LocationSource.Gps;
                locationsPossible = 1;
                //We want the location to be confirmed, we will for now ask the main Handler to do this for us but this may be [TODO]
                localFocusStack.push(new PriorityFocus(focus_location_confirm, 3));
                if (gpsProgress == Progress.inProgress) {
                    gpsProgress = Progress.done;
                } else {
                    gpsProgress = Progress.inProgress;
                }
                return;
            } else { //We don't have geo tag
                localFocusStack.push(new PriorityFocus(focus_enable_gps, 2));
            }
        }
    }

    @Override
    public PriorityFocus peekFocus(Dialogue d) {
        if (localFocusStack.size() == 0) {
            if (location.equals("")) {
                return  new PriorityFocus(focus_location, 1);
            }
            return PriorityFocus.nullFocus();
        }
        return localFocusStack.peek();
    }

    //If the handler has nothing to talk about, this method can be used to initialize new topic
    @Override
    public PriorityFocus popFocus(Dialogue d) {
        if (localFocusStack.size() == 0) {
            if (location.equals("")) {
                return  new PriorityFocus(focus_location, 1);
            }
            return PriorityFocus.nullFocus();
        }
        return localFocusStack.pop();
    }

    public Map<String, String> processResponse(String focus, Map<String, String> responseVariables, Dialogue d) {
        switch(focus) {
            case focus_location_confirm:
                responseVariables.put(slot_out_location, location);
            case focus_landmarks_more:
                responseVariables.put(slot_out_num_locations, Integer.toString(locationsPossible));
            case focus_landmarks_cleared:
            case focus_landmarks:
                landmarksAskedMsgNo = d.getCurrentMessageNumber() + 1;
                d.setRequestingYesNo(true);
                break;
            case focus_landmarks_different:
                responseVariables.put(slot_out_landmark, landmarkBad);
                landmarksAskedMsgNo = d.getCurrentMessageNumber() + 1;
                d.setRequestingYesNo(true);
                break;
            case focus_landmarks_combo_problem:
                d.setRequestingYesNo(true);

        }
        if (acknowledgeChance) {
            acknowledgeChance = false;
            responseVariables.put(slot_out_acknowledgement, "Got it. "); //TODO: set in json
        }

        return responseVariables;
    }

    public String getLocation() {
        if (!locationConfirmed) {
            return "";
        }
        return location;
    }

    public List<String> getLandmarks() {
        return landmarks;
    }

    public boolean validateLandmark(String landmark) {
        NominatimAPIWrapper.NomResult quick_results[] = nom.queryAPI(landmark + ", " + locationGiven, 1, 0, 0);
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
