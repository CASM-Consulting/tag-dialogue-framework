package uk.ac.susx.tag.dialoguer.dialogue.handling;

import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Given a list of intents, merge a subset of them into a single intent and return the new list.
 *
 * User: Andrew D. Robertson
 * Date: 11/05/2015
 * Time: 13:23
 */
public class IntentMerger {

    private List<Intent> intents;

    public IntentMerger(List<Intent> intents){
        this.intents = intents;
    }

    public IntentMerger merge(Set<String> intentsToMerge, String newIntentName){
        intents = merge(intents, intentsToMerge, newIntentName); return this;
    }

    public List<Intent> getIntents() { return intents; }

    /**
     * @param intents the input intents
     * @param intentsToMerge the set of intent names that will be mapped to a single intent
     * @param newIntentName the name of the output merged intent
     */
    public static List<Intent> merge(List<Intent> intents, Set<String> intentsToMerge, String newIntentName){
        Map<Boolean, List<Intent>> requiresMapping = intents.stream()
                                                        .collect(Collectors.partitioningBy(i -> intentsToMerge.contains(i.getName())));
        if (!requiresMapping.get(true).isEmpty()){
            List<Intent> output = requiresMapping.get(false);
            Intent merged = new Intent(newIntentName);

            for (Intent i : requiresMapping.get(true))
                i.getSlotCollection().forEach(merged::fillSlot);

            output.add(merged);
            return output;
        } else return intents;
    }
}
