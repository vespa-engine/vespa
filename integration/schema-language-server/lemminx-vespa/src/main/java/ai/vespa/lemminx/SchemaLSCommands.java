package ai.vespa.lemminx;

import java.lang.reflect.Type;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.lemminx.services.extensions.commands.IXMLCommandService;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Location;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

/**
 * Singleton to interface the Schema Language server through the client.
 */
public class SchemaLSCommands {
    private static final Logger logger = Logger.getLogger(SchemaLSCommands.class.getName());
    private IXMLCommandService commandService;
    private Gson gson;

    private static SchemaLSCommands INSTANCE;
    private SchemaLSCommands() {}
    private SchemaLSCommands(IXMLCommandService commandService) { 
        this.commandService = commandService;
        this.gson = new Gson();
    }

    public static void init(IXMLCommandService commandService) {
        INSTANCE = new SchemaLSCommands(commandService);
    }

    public static SchemaLSCommands instance() {
        return INSTANCE;
    }

    public void sendSetupWorkspaceRequest(String fileURI) {
        commandService.executeClientCommand(new ExecuteCommandParams("vespaSchemaLS.commands.setupWorkspace", List.of(fileURI)));
    }

    public boolean hasSetupWorkspace() {
        Object result = commandService.executeClientCommand(
                new ExecuteCommandParams("vespaSchemaLS.commands.hasSetupWorkspace", List.of())).join();
        if (result == null) return false;
        try {
            String json = gson.toJson(result);
            Type booleanType = new TypeToken<Boolean>() {}.getType();
            return gson.fromJson(json, booleanType);
        } catch (Exception ex) {
            logger.severe("Error when parsing json: " + ex.getMessage());
        }

        return false;
    }

    /**
     * Sends the FIND_SCHEMA_DEFINITION request to the Schema language server.
     */
    public List<Location> findSchemaDefinition(String schemaName) {
        // run sync
        Object findDocumentResult = commandService.executeClientCommand(
            new ExecuteCommandParams("vespaSchemaLS.commands.findSchemaDefinition", List.of(schemaName))).join();

        if (findDocumentResult == null) return List.of();
        try {
            String json = gson.toJson(findDocumentResult);
            Type listOfLocationType = new TypeToken<List<Location>>() {}.getType();
            return gson.fromJson(json, listOfLocationType);
        } catch (Exception ex) {
            logger.severe("Error when parsing json: " + ex.getMessage());
            return List.of();
        }
    }
}
