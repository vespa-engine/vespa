package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.ShowDocumentResult;

import com.google.gson.JsonPrimitive;

import ai.vespa.schemals.context.EventExecuteCommandContext;
import ai.vespa.schemals.lsp.common.command.CommandUtils;

/**
 * OpenDocument
 * Kind of a reflection: client sends "Open document with fileURI", and server responds by telling the client to open it.
 * Reason for this: Used in Code Actions to make file automatically open with new edit. 
 */
public class DocumentOpen implements SchemaCommand {
    private String fileURI;

    @Override
    public Object execute(EventExecuteCommandContext context) {
        if (fileURI == null)
            return null;
        // No return value, as the execution **is** issuing an action on the client side.
        context.messageHandler.showDocument(fileURI).join();
        return null;
    }

    @Override
    public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();

        Optional<String> argument = CommandUtils.getStringArgument(arguments.get(0));
        if (argument.isEmpty()) return false;

        fileURI = argument.get();
        return true;
    }

    @Override
    public int getArity() {
        return 1;
    }
}
