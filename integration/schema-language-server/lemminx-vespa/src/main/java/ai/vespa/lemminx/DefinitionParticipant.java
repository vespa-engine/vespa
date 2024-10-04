package ai.vespa.lemminx;

import java.io.PrintStream;
import java.util.List;

import org.eclipse.lemminx.dom.DOMAttr;
import org.eclipse.lemminx.services.extensions.IDefinitionParticipant;
import org.eclipse.lemminx.services.extensions.IDefinitionRequest;
import org.eclipse.lemminx.services.extensions.commands.IXMLCommandService;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class DefinitionParticipant implements IDefinitionParticipant {
    private PrintStream logger;
    private IXMLCommandService commandService;

    public DefinitionParticipant(PrintStream logger, IXMLCommandService commandService) { 
        this.logger = logger; 
        this.commandService = commandService;
    }

    @Override
    public void findDefinition(IDefinitionRequest request, List<LocationLink> locations, CancelChecker cancelChecker) {

        DOMAttr attribute = request.getCurrentAttribute();

        if (
            attribute != null && 
            attribute.getValue() != null &&
            attribute.getOwnerElement() != null &&
            attribute.getOwnerElement().getNodeName().equals("document")) {
            String schemaFileName = attribute.getValue() + ".sd";
            logger.println("Lets find the definition of document " + schemaFileName);

            Object result = commandService.executeClientCommand(
                new ExecuteCommandParams("vespaSchemaLS.servicesxml.findDocument", List.of(schemaFileName)));
            logger.println("Result: " + result.toString());
        }
    }
}
