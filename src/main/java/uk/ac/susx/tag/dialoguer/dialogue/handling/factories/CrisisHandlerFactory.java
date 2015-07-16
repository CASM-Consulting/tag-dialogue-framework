package uk.ac.susx.tag.dialoguer.dialogue.handling.factories;

import uk.ac.susx.tag.dialoguer.Dialoguer;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.CrisisHandler;
import uk.ac.susx.tag.dialoguer.dialogue.handling.handlers.Handler;

import java.io.IOException;

/**
 * Created by User on 7/15/2015.
 */
public class CrisisHandlerFactory implements HandlerFactory {
    @Override
    public Handler readJson(String resourcePath) throws IOException {
        return new CrisisHandler(Dialoguer.readObjectFromJsonResourceOrFile(resourcePath, CrisisHandler.class));
    }

    @Override
    public String getName() {
        return "crisis_handler";
    }
}
