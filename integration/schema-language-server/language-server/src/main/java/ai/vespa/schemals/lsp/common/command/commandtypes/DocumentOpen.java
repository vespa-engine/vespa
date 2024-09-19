package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.util.List;

import org.eclipse.lsp4j.ShowDocumentResult;

import com.google.gson.JsonPrimitive;

import ai.vespa.schemals.context.EventExecuteCommandContext;

/**
 * OpenDocument
 * Kind of a reflection: client sends "Open document with fileURI", and server responds by telling the client to open it.
 * Reason for this: Used in Code Actions to make file automatically open with new edit. 
 */
public class DocumentOpen implements SchemaCommand {
    private String fileURI;

    @Override
    public void execute(EventExecuteCommandContext context) {
        if (fileURI == null)
            return;
        ShowDocumentResult result = context.messageHandler.showDocument(fileURI).join();
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
