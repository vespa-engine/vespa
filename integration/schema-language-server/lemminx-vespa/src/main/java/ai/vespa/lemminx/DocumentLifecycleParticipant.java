package ai.vespa.lemminx;

import java.util.List;
import java.util.logging.Logger;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.services.extensions.IDocumentLifecycleParticipant;
import org.eclipse.lemminx.services.extensions.commands.IXMLCommandService;
import org.eclipse.lsp4j.ExecuteCommandParams;

public class DocumentLifecycleParticipant implements IDocumentLifecycleParticipant {
    private static final Logger logger = Logger.getLogger(DocumentLifecycleParticipant.class.getName());
    private IXMLCommandService commandService;

    public DocumentLifecycleParticipant(IXMLCommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public void didOpen(DOMDocument document) {
        try {
            String fileURI = document.getTextDocument().getUri();
            SchemaLSCommands.instance().sendSetupWorkspaceRequest(fileURI);
        } catch (Exception ex) {
            // not very severe from our point of view
            logger.warning("Error when issuing setup workspace command: " + ex.getMessage());
        }
    }

    @Override
    public void didChange(DOMDocument document) {
    }

    @Override
    public void didSave(DOMDocument document) {
    }

    @Override
    public void didClose(DOMDocument document) {
    }
}
