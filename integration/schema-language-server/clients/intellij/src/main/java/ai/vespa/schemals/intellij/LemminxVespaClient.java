package ai.vespa.schemals.intellij;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lemminx.customservice.XMLLanguageClientAPI;

import java.util.concurrent.CompletableFuture;

/*
The XMLLanguageClientAPI from LemMinX declares the custom LSP extensions they made.
It is two things:
- JSON Notification: "actionableNotification" (throws UnsupportedOperationException unless implemented here)
- JSON Request: "executeClientCommand" (this is the reason we implement it).
 */
public class LemminxVespaClient extends LanguageClientImpl implements XMLLanguageClientAPI {
    public LemminxVespaClient(Project project) { super(project); }

    @Override
    public CompletableFuture<Object> executeClientCommand(ExecuteCommandParams params) {
        super.logMessage(new MessageParams(MessageType.Info, "Execute: " + params.toString()));
        return CompletableFutures.computeAsync(cancelChecker -> {
            return null;
        });
    }
}
