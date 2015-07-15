package uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.interactiveIntentHandlers;

import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.InteractiveHandler;
import uk.ac.susx.tag.dialoguer.knowledge.location.NominatimAPIWrapper;
import uk.ac.susx.tag.dialoguer.knowledge.location.RadiusAssigner;

import java.util.*;

/**
 * Created by Daniel Saska on 6/27/2015.
 */
public class LandmarkProblemHandler implements Handler.ProblemHandler {
    NominatimAPIWrapper nom = new NominatimAPIWrapper();
    List<String> landmarks = new ArrayList<>();

    private static final int maxDiamter = 200;

    @Override
    public boolean isInHandleableState(List<Intent> intents, Dialogue dialogue) {
        if (dialogue.isEmptyFocusStack()) { return false; }
        boolean intentmatch = intents.stream().filter(i->i.getName().equals(InteractiveHandler.landmarkIntent)).count()>0;
        boolean intentmatch2 = intents.stream().filter(i->i.getName().equals(Intent.choice)).count()>0;
        boolean intentmatch3 = intents.stream().filter(i->i.getName().equals(Intent.allChoice)).count()>0;
        boolean intentmatch4 = intents.stream().filter(i->i.getName().equals(Intent.noChoice)).count()>0;
        boolean intentmatch5 = intents.stream().filter(i->i.getName().equals(Intent.nullChoice)).count()>0;
        boolean statematch = dialogue.peekTopFocus().equals(InteractiveHandler.aLandmarks);
        statematch |= dialogue.peekTopFocus().equals(InteractiveHandler.aLeaveLandmark);
        return (intentmatch || intentmatch2 || intentmatch3 || intentmatch4 || intentmatch5) && statematch;
    }

    @Override
    public void handle(List<Intent> intents, Dialogue dialogue, Object resource) {
        System.err.println("landmark intent handler fired");
        Intent intent = intents.stream().filter(i -> i.getSource().equals(InteractiveHandler.multichocieIntent)).filter(i->i.isName(Intent.choice)).findFirst().orElse(null);
        if (dialogue.peekTopFocus().equals(InteractiveHandler.aLandmarks)) {
            if (intent != null) {
                Iterator<Intent.Slot> it = intent.getSlotByType("choice").iterator();
                while (it.hasNext()) {
                    int idx = Integer.parseInt(it.next().value);
                    landmarks.set(idx, null);
                }
                {
                    int i = landmarks.size();
                    while (i-- > 0) {
                        if (landmarks.get(i) == null) {
                            landmarks.remove(i);
                        }
                    }
                }
                List<NominatimAPIWrapper.NomResult> instances[] = new List[landmarks.size()];
                for (int i = 0; i < landmarks.size(); ++i) {
                    NominatimAPIWrapper.NomResult results[] = nom.queryAPI(landmarks.get(i) + ", " + dialogue.getFromWorkingMemory("location_given"), 200, 0, 1);
                    instances[i] = Arrays.asList(results);
                }
                List<List<NominatimAPIWrapper.NomResult>> areas = new ArrayList<>();
                buildAreas(0, instances, areas);
                dialogue.putToWorkingMemory("n_loc", Integer.toString(areas.size()));
                dialogue.pushFocus(InteractiveHandler.qAddLandmarks);
                dialogue.clearChoices();
                return;
            }
            intent = intents.stream().filter(i -> i.getSource().equals(InteractiveHandler.multichocieIntent)).filter(i -> i.isName(Intent.allChoice)).findFirst().orElse(null);
            if (intent != null) {
                landmarks.clear();
                List<NominatimAPIWrapper.NomResult> instances[] = new List[landmarks.size()];
                for (int i = 0; i < landmarks.size(); ++i) {
                    NominatimAPIWrapper.NomResult results[] = nom.queryAPI(landmarks.get(i) + ", " + dialogue.getFromWorkingMemory("location_given"), 200, 0, 1);
                    instances[i] = Arrays.asList(results);
                }
                dialogue.putToWorkingMemory("n_loc", "0");
                dialogue.pushFocus(InteractiveHandler.qAddLandmarks);
                dialogue.clearChoices();
                return;
            }
            intent = intents.stream().filter(i -> i.getSource().equals(InteractiveHandler.multichocieIntent)).filter(i -> i.isName(Intent.nullChoice)).findFirst().orElse(null);
            if (intent != null) {
                dialogue.pushFocus(InteractiveHandler.aLeaveLandmark);
                dialogue.pushFocus(InteractiveHandler.qLeaveLandmark);
                List<String> choices = new ArrayList<>();
                choices.add("provide an address manually");
                choices.add("use GPS on my device");
                dialogue.setChoices(choices);
                return;
            }
        }
        intent = intents.stream().filter(i -> i.getSource().equals(InteractiveHandler.choiceIntent)).filter(i->i.isName(Intent.nullChoice)).findFirst().orElse(null);
        if (intent != null) {
            dialogue.pushFocus("apology");
            return;
        }
        intent = intents.stream().filter(i -> i.getSource().equals(InteractiveHandler.choiceIntent)).filter(i->i.isName(Intent.choice)).findFirst().orElse(null);
        if (intent != null) {
            if (intent.getSlotByType("choice").iterator().next().value.equals("0")) {
                dialogue.pushFocus(InteractiveHandler.aLocation);
                dialogue.pushFocus(InteractiveHandler.qLocation);
            }
            if (intent.getSlotByType("choice").iterator().next().value.equals("1")) {
                InteractiveHandler.handleGps(dialogue);
            }
            return;
        }
        intent = intents.stream().filter(i -> i.getSource().equals(InteractiveHandler.multichocieIntent)).filter(i->i.isName(Intent.noChoice)).findFirst().orElse(null);
        if (intent == null) {
            intent = intents.stream().filter(i -> i.getSource().equals(InteractiveHandler.choiceIntent)).filter(i->i.isName(Intent.noChoice)).findFirst().orElse(null);
        }
        if (intent != null) {
            dialogue.pushFocus(InteractiveHandler.qChoiceRephrase);
            return;
        }
        intent = intents.stream().filter(i -> i.isName(InteractiveHandler.landmarkIntent)).findFirst().orElse(null);
        if(intent != null && intent.getSlotByType("place") != null && !intent.getSlotByType("place").iterator().next().value.equals("")) {

            NominatimAPIWrapper.NomResult quick_results[] = nom.queryAPI(intent.getSlotByType("place").iterator().next().value.replace("¥", "") + ", " + dialogue.getFromWorkingMemory("location_given"), 1, 0, 0);
            if (quick_results.length == 0) {
                dialogue.pushFocus(InteractiveHandler.landmarkNotFound);
                return;
            }
            landmarks.add(intent.getSlotByType("place").iterator().next().value);


            List<NominatimAPIWrapper.NomResult> instances[] = new List[landmarks.size()];
            for (int i = 0; i < landmarks.size(); ++i) {
                NominatimAPIWrapper.NomResult results[] = nom.queryAPI(landmarks.get(i) + ", " + dialogue.getFromWorkingMemory("location_given"), 200, 0, 1);
                instances[i] = Arrays.asList(results);
            }
            List<List<NominatimAPIWrapper.NomResult>> areas = new ArrayList<>();
            buildAreas(0, instances, areas);
            dialogue.putToWorkingMemory("n_loc", Integer.toString(areas.size()));
            if (areas.size() == 1) { //Found precise position
                String addressName = "";
                if (!areas.get(0).get(0).address.keySet().iterator().next().equals("road")
                        && !areas.get(0).get(0).address.keySet().iterator().next().equals("house_number")
                        && !areas.get(0).get(0).address.keySet().iterator().next().equals("footway") ) {
                    addressName += areas.get(0).get(0).address.values().iterator().next() + ", ";
                }
                if (areas.get(0).get(0).address.get("house_number") != null) {
                    addressName += areas.get(0).get(0).address.get("house_number") + ", ";
                }
                if (areas.get(0).get(0).address.get("road") != null) {
                    addressName += areas.get(0).get(0).address.get("road") + ", ";
                } else if (areas.get(0).get(0).address.get("footway") != null) {
                    addressName += areas.get(0).get(0).address.get("footway") + ", ";

                }
                if (areas.get(0).get(0).address.get("city") != null) {
                    addressName += areas.get(0).get(0).address.get("city") + ", ";
                } else if (areas.get(0).get(0).address.get("town") != null) {
                    addressName += areas.get(0).get(0).address.get("town") + ", ";
                }
                addressName += areas.get(0).get(0).address.get("county") + ", ";
                addressName += areas.get(0).get(0).address.get("postcode");
                dialogue.putToWorkingMemory("location_processed", addressName);
                dialogue.putToWorkingMemory(InteractiveHandler.addressConfirmFlag, "");
                ((InteractiveHandler)resource).finalizeRequest(dialogue);
                landmarks.clear();
                return;
            }
            if (areas.size() == 0) {
                dialogue.setChoices(landmarks);
                String landmarkList = "";
                for (String l : landmarks) {
                    landmarkList += l + ", ";
                }
                landmarkList = landmarkList.substring(0,landmarkList.length() - 2);
                dialogue.putToWorkingMemory("landmarks", landmarkList);
                dialogue.pushFocus(InteractiveHandler.qLandmarksRemove);
                return;
            }
            dialogue.pushFocus(InteractiveHandler.qAddLandmarks);
        }
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

