package ai.vespa.schemals.lsp.command.commandtypes;

import java.util.List;

import org.eclipse.lsp4j.ShowDocumentResult;

import com.google.gson.JsonPrimitive;

import ai.vespa.schemals.context.EventExecuteCommandContext;

/**
 * OpenDocument
 */
public class DocumentOpen implements SchemaCommand {
    private String fileURI;

    @Override
    public void execute(EventExecuteCommandContext context) {
        if (fileURI == null)
            return;
        context.logger.info("Show document: " + fileURI);
        ShowDocumentResult result = context.messageHandler.showDocument(fileURI).join();
        context.logger.info("Result: " + result.toString());
    }

    @Override
    public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();

        if (!(arguments.get(0) instanceof JsonPrimitive))
            return false;

        JsonPrimitive arg = (JsonPrimitive) arguments.get(0);
        fileURI = arg.getAsString();
        return true;
    }

    @Override
    public int getArity() {
        return 1;
    }
}
