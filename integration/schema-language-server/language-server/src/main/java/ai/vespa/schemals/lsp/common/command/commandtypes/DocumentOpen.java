package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

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

        URI uriToOpen;
        try {
            uriToOpen = new URI(fileURI);
            if (uriToOpen.getScheme() == null)
                throw new URISyntaxException(fileURI, "Expected a scheme.");
        } catch(URISyntaxException ex) {
            if (context.scheduler.getWorkspaceURI() == null) return null;

            uriToOpen = URI.create(context.scheduler.getWorkspaceURI()).resolve(fileURI);
        }
        context.logger.info("Show document: " + uriToOpen.toString());
        // No return value, as the execution **is** issuing an action on the client side.
        context.messageHandler.showDocument(uriToOpen.toString()).join();
        return null;
    }

    @Override
    public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();

        Optional<String> argument = CommandUtils.getStringArgument(arguments.get(0));
        if (argument.isEmpty()) return false;

        this.fileURI = argument.get();

        return true;
    }

    @Override
    public int getArity() {
        return 1;
    }
}
