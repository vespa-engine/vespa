package ai.vespa.lemminx.participants;

import java.util.logging.Logger;

import org.eclipse.lemminx.services.extensions.completion.ICompletionParticipant;
import org.eclipse.lemminx.services.extensions.completion.ICompletionRequest;
import org.eclipse.lemminx.services.extensions.completion.ICompletionResponse;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

import ai.vespa.lemminx.command.SchemaLSCommands;

public class CompletionParticipant implements ICompletionParticipant {
    private static final Logger logger = Logger.getLogger(CompletionParticipant.class.getName());

    @Override
    public void onTagOpen(ICompletionRequest completionRequest, ICompletionResponse completionResponse,
            CancelChecker cancelChecker) throws Exception {
    }

    @Override
    public void onXMLContent(ICompletionRequest request, ICompletionResponse response, CancelChecker cancelChecker)
            throws Exception {
    }

    @Override
    public void onAttributeName(boolean generateValue, ICompletionRequest request, ICompletionResponse response,
            CancelChecker cancelChecker) throws Exception {
    }

    @Override
    public void onAttributeValue(String valuePrefix, ICompletionRequest request, ICompletionResponse response,
            CancelChecker cancelChecker) throws Exception {
        if ("document".equals(request.getCurrentTag()) && "type".equals(request.getCurrentAttributeName())) {
            for (String schemaName : SchemaLSCommands.instance().getDefinedSchemas()) {
                response.addCompletionItem(new CompletionItem(schemaName));
            }
        }
    }

    @Override
    public void onDTDSystemId(String valuePrefix, ICompletionRequest request, ICompletionResponse response,
            CancelChecker cancelChecker) throws Exception { }
}
