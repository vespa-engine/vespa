package ai.vespa.lemminx.participants;

import java.util.List;
import java.util.logging.Logger;

import org.eclipse.lemminx.dom.DOMAttr;
import org.eclipse.lemminx.services.extensions.IDefinitionParticipant;
import org.eclipse.lemminx.services.extensions.IDefinitionRequest;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

import ai.vespa.lemminx.command.SchemaLSCommands;

public class DefinitionParticipant implements IDefinitionParticipant {
    private static final Logger logger = Logger.getLogger(DefinitionParticipant.class.getName());

    @Override
    public void findDefinition(IDefinitionRequest request, List<LocationLink> locations, CancelChecker cancelChecker) {

        DOMAttr attribute = request.getCurrentAttribute();

        if (
            attribute != null && 
            attribute.getValue() != null &&
            attribute.getOwnerElement() != null &&
            attribute.getOwnerElement().getNodeName().equals("document")) {
            String schemaName = attribute.getValue();

            List<Location> locationResult = SchemaLSCommands.instance().findSchemaDefinition(schemaName);

            locationResult.stream()
                          .map(loc -> new LocationLink(loc.getUri(), loc.getRange(), loc.getRange()))
                          .forEach(locations::add);
        }
    }

}
