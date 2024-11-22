package ai.vespa.schemals.intellij;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerManager;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.commands.CommandExecutor;
import com.redhat.devtools.lsp4ij.commands.LSPCommandContext;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lemminx.customservice.XMLLanguageClientAPI;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/*
The XMLLanguageClientAPI from LemMinX declares the custom LSP extensions they made.
It is two things:
- JSON Notification: "actionableNotification" (throws UnsupportedOperationException unless implemented here)
- JSON Request: "executeClientCommand" (this is the reason we implement it).
 */
public class LemminxVespaClient extends LanguageClientImpl implements XMLLanguageClientAPI {

    private static final Map<String, String> clientVespaCommands = Map.of(
            "vespaSchemaLS.commands.schema.findSchemaDefinition", "FIND_SCHEMA_DEFINITION",
            "vespaSchemaLS.commands.schema.setupWorkspace", "SETUP_WORKSPACE",
            "vespaSchemaLS.commands.schema.hasSetupWorkspace", "HAS_SETUP_WORKSPACE",
            "vespaSchemaLS.commands.schema.createSchemaFile", "CREATE_SCHEMA_FILE",
            "vespaSchemaLS.commands.schema.getDefinedSchemas", "GET_DEFINED_SCHEMAS"
    );

    public LemminxVespaClient(Project project) { super(project); }

    @Override
    public CompletableFuture<Object> executeClientCommand(ExecuteCommandParams params) {
        super.logMessage(new MessageParams(MessageType.Info, "Execute: " + params.toString()));
        String commandKey = params.getCommand();

        if (!clientVespaCommands.containsKey(commandKey)) {
            return null;
        }

        // Forward command to vespa schema LS
        Command command = new Command("SchemaCommand", clientVespaCommands.get(commandKey));
        command.setArguments(params.getArguments());
        LSPCommandContext context = new LSPCommandContext(command, getProject());
        context.setPreferredLanguageServerId("vespaSchemaLanguageServer");
        return CommandExecutor.executeCommand(context).response();
    }
}
