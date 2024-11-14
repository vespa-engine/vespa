package ai.vespa.lemminx.participants;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.logging.Logger;

import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionParticipant;
import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionRequest;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

import com.google.gson.JsonPrimitive;

import ai.vespa.lemminx.participants.DiagnosticsParticipant.DiagnosticCode;

public class CodeActionParticipant implements ICodeActionParticipant {
    private static final Logger logger = Logger.getLogger(CodeActionParticipant.class.getName());

    @Override
    public void doCodeAction(ICodeActionRequest request, List<CodeAction> codeActions, CancelChecker cancelChecker) throws CancellationException {
        Diagnostic diagnostic = request.getDiagnostic();
        if (diagnostic.getCode() != null && diagnostic.getCode().isRight()) {
            Integer diagnosticNumber = diagnostic.getCode().getRight();
            DiagnosticCode code = DiagnosticsParticipant.codeFromInt(diagnosticNumber);
            if (code.equals(DiagnosticCode.DOCUMENT_DOES_NOT_EXIST)) {
                documentDoesNotExistAction(diagnostic).ifPresent(action -> codeActions.add(action));
            }
        }
    }

    private Optional<CodeAction> documentDoesNotExistAction(Diagnostic diagnostic) {
        if (diagnostic.getData() == null) return Optional.empty();

        Object jsonObject = diagnostic.getData();
        if (!(jsonObject instanceof JsonPrimitive))
            return Optional.empty();
        JsonPrimitive arg = (JsonPrimitive) jsonObject;
        String documentName = arg.getAsString();

        CodeAction codeAction = new CodeAction("Create document");
        codeAction.setKind(CodeActionKind.QuickFix);
        codeAction.setCommand(new Command(
            "Create Schema File",
            "vespaSchemaLS.commands.schema.createSchemaFile",
            List.of(documentName)
        ));
        return Optional.of(codeAction);
    }
}

