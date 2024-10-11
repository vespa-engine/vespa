package ai.vespa.lemminx;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lemminx.dom.DOMAttr;
import org.eclipse.lemminx.services.extensions.IDefinitionParticipant;
import org.eclipse.lemminx.services.extensions.IDefinitionRequest;
import org.eclipse.lemminx.services.extensions.commands.IXMLCommandService;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

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
            String schemaName = attribute.getValue();

            // run sync
            Object result = commandService.executeClientCommand(
                new ExecuteCommandParams("vespaSchemaLS.servicesxml.findDocument", List.of(schemaName))).join();

            List<Location> locationResult = parseFindDocumentResult(result);

            locationResult.stream()
                          .map(loc -> new LocationLink(loc.getUri(), loc.getRange(), loc.getRange()))
                          .forEach(locations::add);
        }
    }

    /**
     * Converts from LinkedTreeMap based structure to typed LSP4J structure.
     */
    private List<Location> parseFindDocumentResult(Object findDocumentResult) {
        if (findDocumentResult == null) return List.of();
        try {
            Gson gson = new Gson();
            String json = gson.toJson(findDocumentResult);
            Type listOfLocationType = new TypeToken<List<Location>>() {}.getType();
            return gson.fromJson(json, listOfLocationType);
        } catch (Exception ex) {
            logger.println("Error when parsing json: " + ex.getMessage());
            return List.of();
        }
    }
}
