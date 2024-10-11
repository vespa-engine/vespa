package ai.vespa.lemminx;

import java.io.PrintStream;
import java.util.List;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.services.extensions.IDocumentLifecycleParticipant;
import org.eclipse.lemminx.services.extensions.commands.IXMLCommandService;
import org.eclipse.lsp4j.ExecuteCommandParams;

public class DocumentLifecycleParticipant implements IDocumentLifecycleParticipant {

    private PrintStream logger;
    private IXMLCommandService commandService;

    public DocumentLifecycleParticipant(PrintStream logger, IXMLCommandService commandService) {
        this.logger = logger;
        this.commandService = commandService;
    }

    @Override
    public void didOpen(DOMDocument document) {
        try {
            String fileURI = document.getTextDocument().getUri();
            commandService.executeClientCommand(new ExecuteCommandParams("vespaSchemaLS.servicesxml.setupWorkspace", List.of(fileURI)));
        } catch (Exception ex) {
            logger.println("Error when issuing setup workspce command: " + ex.getMessage());
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
