package ai.vespa.lemminx.participants;

import java.util.List;
import java.util.logging.Logger;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.services.extensions.IDocumentLifecycleParticipant;
import org.eclipse.lemminx.services.extensions.commands.IXMLCommandService;
import org.eclipse.lsp4j.ExecuteCommandParams;

import ai.vespa.lemminx.VespaExtension;
import ai.vespa.lemminx.command.SchemaLSCommands;
import ai.vespa.lemminx.index.ServiceDocument;

public class DocumentLifecycleParticipant implements IDocumentLifecycleParticipant {
    private static final Logger logger = Logger.getLogger(DocumentLifecycleParticipant.class.getName());
    private IXMLCommandService commandService;
    private ServiceDocument serviceDocument;

    public DocumentLifecycleParticipant(IXMLCommandService commandService, ServiceDocument serviceDocument) {
        this.commandService = commandService;
        this.serviceDocument = serviceDocument;
    }

    @Override
    public void didOpen(DOMDocument document) {
        if (!VespaExtension.match(document))
            return;

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
        serviceDocument.didChange(document);
    }

    @Override
    public void didSave(DOMDocument document) {
    }

    @Override
    public void didClose(DOMDocument document) {
    }
}
