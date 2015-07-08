package uk.ac.susx.tag.dialoguer.dialogue.handling.factories;

import uk.ac.susx.tag.dialoguer.Dialoguer;
import uk.ac.susx.tag.dialoguer.dialogue.analysing.analysers.NerQuestionAnalyser;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.InteractiveHandler;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;

import java.io.IOException;

/**
 * Created by Daniel Saska on 6/25/2015.
 */
public class InteractiveHandlerFactory implements HandlerFactory {
    @Override
    public Handler readJson(String resourcePath) throws IOException {
        return new InteractiveHandler(Dialoguer.readObjectFromJsonResourceOrFile(resourcePath, InteractiveHandler.class));
    }

    @Override
    public String getName() {
        return "question_handler";
    }
}
