package uk.ac.susx.tag.dialoguer.dialogue.analysing.analysers;

/**
 * Created by Daniel Saska on 6/30/2015.
 */

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import uk.ac.susx.tag.dialoguer.dialogue.analysing.factories.AnalyserFactory;
import uk.ac.susx.tag.dialoguer.dialogue.analysing.factories.ChoiceMakingAnalyserStringMatchingFactory;
import uk.ac.susx.tag.dialoguer.dialogue.components.Dialogue;
import uk.ac.susx.tag.dialoguer.dialogue.components.Intent;
import uk.ac.susx.tag.dialoguer.knowledge.linguistic.EnglishStemmer;
import uk.ac.susx.tag.dialoguer.knowledge.linguistic.Numbers;
import uk.ac.susx.tag.dialoguer.knowledge.linguistic.SimplePatterns;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tries to determine what choices the user is making when the Handler has indicated that it has presented choices to
 * the user. Supports multiple selections.
 *
 * Uses the default choices intents. See Intent documentation.
 */
public class MultichoiceMakingAnalyserStringMatching extends Analyser  {
    private double choiceFraction = 0.5;
    private boolean multichoice = false;

    public MultichoiceMakingAnalyserStringMatching() {

    }

    public MultichoiceMakingAnalyserStringMatching(double choiceFraction, boolean multichoice){
        this.choiceFraction = choiceFraction;
        this.multichoice = multichoice;
    }

    public MultichoiceMakingAnalyserStringMatching(MultichoiceMakingAnalyserStringMatching other) {
        super();
        choiceFraction = other.choiceFraction;
        multichoice = other.multichoice;
    }

    public static final Set<String> nullChoicePhrases = Sets.newHashSet(
            "none",
            "no",
            "none of them",
            "no thanks",
            "neither"
    );

    public static final Set<String> allChoicePhrases = Sets.newHashSet(
            "all",
            "all of them",
            "everything",
            "every one of them",
            "every one",
            "every single one",
            "choose all",
            "all choices"
    );

    public static boolean isMultichoice(Dialogue d, List<String> choices, double threshold) {
        String userMessage = d.getLastMessage().getText();

        String[] candidates = userMessage.split(",|[a][n][d]");

        //When listing choices, user can either a) have only the list, b) have custom string followed by list, c) have list followed by custom string d) combination of b and c
        //Therefore for numbered listings all elements but first and last have to be numeric. It also makes little to no sense to mix numerical and string-identified choices
        //to be combined so we will generally ignore that possibility.
        boolean numeric = true;

        for (int i = 0; i < candidates.length; ++i) {

            boolean fl = false;
            if (i == 0) {
                try {
                    String[] frst = candidates[i].split("\\s+");
                    Integer.toString(Numbers.parseNumber(frst[frst.length-1])); //Try to parse last token of the sequence
                    fl = true;
                } catch (NumberFormatException e) {
                }
            }
            if (i == candidates.length - 1) {
                try {
                    String[] last = candidates[i].split("\\s+");
                    Integer.toString(Numbers.parseNumber(last[0])); //Try to parse last token of the sequence
                    fl = true;
                } catch (NumberFormatException e) {
                }
            }

            String candidate = candidates[i].replaceAll("\\s+","");
            try {
                Integer.toString(Numbers.parseNumber(candidate));
            } catch (NumberFormatException e) {
                if (fl == false) {
                    numeric = false;
                    break;
                }
            }

        }
        if (numeric == true) {
            return true;
        }
        // Set of unique words in remaining message, normalising any number strings
//            Set<String> uniqueWordsRemaining = Sets.newHashSet(
//                    Arrays.stream(SimplePatterns.whitespaceRegex.split(userMessage))
//                            .map(Numbers::convertIfNumber)
//                            .collect(Collectors.toList())
//            );

        boolean ret = true;
        for (String cand : candidates) {
            EnglishStemmer stemmer = new EnglishStemmer();

            String nopunc = SimplePatterns.stripPunctuation(cand.toLowerCase());
            Set<String> uniqueWordsRemaining = Sets.newHashSet(SimplePatterns.whitespaceRegex.split(SimplePatterns.stripPunctuation(cand.toLowerCase()))).stream()
                    .map(stemmer::stem)
                    .collect(Collectors.toSet());

            // Given each possible choice, find the maximum fraction of words in the user message that appear in a choice
            double maxFractionDescribed = choices.stream()
                    .mapToDouble((c) -> {
                        Set<String> uniqueWordsInChoice = Sets.newHashSet(SimplePatterns.splitByWhitespace(SimplePatterns.stripPunctuation(c.toLowerCase()))).stream()
                                .map(stemmer::stem)
                                .collect(Collectors.toSet());
                        return 1 - (Sets.difference(uniqueWordsRemaining, uniqueWordsInChoice).size() / (double) uniqueWordsRemaining.size());
                    })
                    .max().getAsDouble();
            ret = (maxFractionDescribed >= threshold) && ret;
        }

        return ret;
    }

    /**
     * Try to find the most likely choices being made, by first checking for a number, then if failed, use levenshtein
     * distance for each element of the (comma-separted) list
     */
    public static List<Integer> whichChoices(Dialogue d, List<String> choices, double threshold) {
        if (choices.size() == 0) throw new RuntimeException("There must be at least one choice");

        String userMessage = d.getLastMessage().getText();

        String[] candidates = userMessage.split("and|,");

        //When listing choices, user can either a) have only the list, b) have custom string followed by list, c) have list followed by custom string d) combination of b and c
        //Therefore for numbered listings all elements but first and last have to be numeric. It also makes little to no sense to mix numerical and string-identified choices
        //to be combined so we will generally ignore that possibility.
        boolean numeric = true;

        List<Integer> ret = new ArrayList<>();

        for (int i = 0; i < candidates.length; ++i) {

            boolean fl = false;
            if (i == 0) {
                try {
                    String[] frst = candidates[i].split("\\s+");
                    if (Numbers.parseNumber(frst[frst.length-1])-1<choices.size()) {
                        ret.add(Numbers.parseNumber(frst[frst.length-1])-1);
                    } else {
                        throw new NumberFormatException();
                    }
                    fl = true;
                } catch (NumberFormatException e) {
                }
            }
            if (i == candidates.length - 1) {
                try {
                    String[] last = candidates[i].split("\\s+");
                    if (Numbers.parseNumber(last[0])-1<choices.size()) {
                        ret.add(Numbers.parseNumber(last[0])-1);
                    } else {
                        throw new NumberFormatException();
                    }
                    fl = true;
                } catch (NumberFormatException e) {
                }
            }

            String candidate = candidates[i].replaceAll("\\s+","");
            try {
                if (fl == false) {
                    if (Numbers.parseNumber(candidate) - 1 < choices.size()) {
                        ret.add(Numbers.parseNumber(candidate) - 1);
                    } else {
                        throw new NumberFormatException();
                    }
                }
            } catch (NumberFormatException e) {
                if (fl == false) {
                    numeric = false;
                    break;
                }
            }

        }
        if (numeric == true) {
            return ret;
        }
        ret = new ArrayList<>();

        for (String cand : candidates) {
            cand = cand.toLowerCase();
            int minLength = choices.stream().max(Comparator.comparing(String::length)).get().length();

            int closestChoice = 0;
            int closestDistance = Integer.MAX_VALUE;

            for (int i = 0; i < choices.size(); i++) {
                String choice = choices.get(i).toLowerCase();
                int distance = StringUtils.getLevenshteinDistance(cand, Strings.padEnd(choice, minLength, ' ')); //Pad the strings for fair comparison
                if (distance < closestDistance) {
                    closestChoice = i;
                    closestDistance = distance;
                }
            }
            ret.add(closestChoice);
        }
        return ret;
    }

    /**
     * If the stripped message is equal to any of the null choice messages, it is a null choice.
     */
    public static boolean isNullChoice(Dialogue d){
        return nullChoicePhrases.contains(d.getFromWorkingMemory("stripped"));
    }

    /**
     * If the stripped message is equal to any of the null choice messages, it is a null choice.
     */
    public static boolean isAllChoice(Dialogue d){
        return allChoicePhrases.contains(d.getFromWorkingMemory("stripped"));
    }

    /**
     * If a choice has been presented to the user (the Dialogue's "choices" field is non-empty):
     *   If the user explicitly rejected the choices, return "null_choice" intent
     *   Otherwise, if the user selected a choice, return a "choice" intent, with a slot detailing the choice
     *   Otherwise, return a "no_choice" intent
     * Otherwise return no intents
     */
    @Override
    public List<Intent> analyse(String message, Dialogue d) {
        if (d.isChoicesPresented()){
            if (isNullChoice(d)){
                return Intent.buildNullChoiceIntent(message).toList();
            } else if (isAllChoice(d)) {
                return Intent.buildAllChoiceIntent(message).toList();
            } else if (isMultichoice(d, d.getChoices(), choiceFraction)){
                List<Integer> choices = whichChoices(d, d.getChoices(), choiceFraction);
                if (choices == null) { return Intent.buildNoChoiceIntent(message).toList(); }
                return Intent.buildMultichoiceIntent(message, choices).toList();
            } else {
                return Intent.buildNoChoiceIntent(message).toList();
            }
        }
        return new ArrayList<>();
    }

    @Override
    public AnalyserFactory getFactory() {
        return new ChoiceMakingAnalyserStringMatchingFactory();
    }

    @Override
    public void close() throws Exception {
        // No resources to close
    }
}
