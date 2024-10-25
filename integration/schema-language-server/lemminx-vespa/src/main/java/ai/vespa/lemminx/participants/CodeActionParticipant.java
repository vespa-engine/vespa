package ai.vespa.lemminx.participants;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.logging.Logger;

import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionParticipant;
import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionRequest;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class CodeActionParticipant implements ICodeActionParticipant {
    private static final Logger logger = Logger.getLogger(CodeActionParticipant.class.getName());

    @Override
    public void doCodeAction(ICodeActionRequest request, List<CodeAction> codeActions, CancelChecker cancelChecker) throws CancellationException {
        logger.info(request.getDiagnostic().toString());
    }
}

