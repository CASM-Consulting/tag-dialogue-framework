package uk.ac.susx.tag.dialoguer.dialogue.analysing.factories;

import uk.ac.susx.tag.dialoguer.Dialoguer;
import uk.ac.susx.tag.dialoguer.dialogue.analysing.analysers.Analyser;
import uk.ac.susx.tag.dialoguer.dialogue.analysing.analysers.ChoiceMakingAnalyserStringMatching;
import uk.ac.susx.tag.dialoguer.dialogue.analysing.analysers.MultichoiceMakingAnalyserStringMatching;

import java.io.IOException;

/**
 * Created by Daniel Saska on 6/30/2015.
 */
public class MultichoiceMakingAnalyserStringMatchingFactory implements AnalyserFactory{

    @Override
    public String getName() {
        return "simple_multichoice";
    }

    public Analyser readJson(String resourcePath) throws IOException {
        if (resourcePath == null) {
            return new ChoiceMakingAnalyserStringMatching(0.5);
        }
        return new MultichoiceMakingAnalyserStringMatching(Dialoguer.readObjectFromJsonResourceOrFile(resourcePath, MultichoiceMakingAnalyserStringMatching.class));
    }
}
