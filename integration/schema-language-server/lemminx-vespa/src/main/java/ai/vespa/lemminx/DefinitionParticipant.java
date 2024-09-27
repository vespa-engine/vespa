package ai.vespa.lemminx;

import java.io.PrintStream;
import java.util.List;

import org.eclipse.lemminx.dom.DOMAttr;
import org.eclipse.lemminx.services.extensions.IDefinitionParticipant;
import org.eclipse.lemminx.services.extensions.IDefinitionRequest;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class DefinitionParticipant implements IDefinitionParticipant {
    private PrintStream logger;

    public DefinitionParticipant(PrintStream logger) { this.logger = logger; }

    @Override
    public void findDefinition(IDefinitionRequest request, List<LocationLink> locations, CancelChecker cancelChecker) {

        DOMAttr attribute = request.getCurrentAttribute();

        if (
            attribute != null && 
            attribute.getValue() != null &&
            attribute.getOwnerElement() != null &&
            attribute.getOwnerElement().getNodeName().equals("document")) {
            logger.println("Lets find the definition of document " + attribute.getValue() + ".sd");
        }
    }
}
