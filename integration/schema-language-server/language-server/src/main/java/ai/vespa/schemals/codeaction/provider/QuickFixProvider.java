package ai.vespa.schemals.codeaction.provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.EventCodeActionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.rename.SchemaRename;
import ai.vespa.schemals.schemadocument.SchemaDocument;

/**
 * QuickfixAction
 * Responsible for giving quickfixes for the given code action request
 */
public class QuickFixProvider implements CodeActionProvider {

    private WorkspaceEdit simpleEdit(EventCodeActionContext context, Range range, String newText) {
        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        TextDocumentEdit textDocumentEdit = new TextDocumentEdit();
        TextEdit textEdit = new TextEdit();
        textDocumentEdit.setTextDocument(context.document.getVersionedTextDocumentIdentifier());
        textEdit.setRange(range);
        textEdit.setNewText(newText);
        textDocumentEdit.setEdits(List.of(textEdit));
        workspaceEdit.setDocumentChanges(List.of(Either.forLeft(textDocumentEdit)));
        return workspaceEdit;
    }

    private CodeAction schemaNameSameAsFileFix(EventCodeActionContext context, Diagnostic diagnostic) {
        CodeAction action = new CodeAction();

        String requiredName = FileUtils.schemaNameFromPath(context.document.getFileURI());
        action.setTitle("Rename schema to " + requiredName);
        action.setKind(CodeActionKind.QuickFix);
        action.setDiagnostics(List.of(diagnostic));
        //action.setEdit(simpleEdit(context, diagnostic.getRange(), requiredName));
        action.setEdit(SchemaRename.rename(context, requiredName));
        return action;
    }

    private CodeAction documentNameSameAsSchemaFix(EventCodeActionContext context, Diagnostic diagnostic) {
        if (!(context.document instanceof SchemaDocument)) return null; // basically unreachable
        SchemaDocument document = (SchemaDocument)context.document;
        if (document.getSchemaIdentifier() == null) return null; // quickfix impossible

        CodeAction action = new CodeAction();
        action.setTitle("Rename document to " + document.getSchemaIdentifier());
        action.setKind(CodeActionKind.QuickFix);
        action.setDiagnostics(List.of(diagnostic));

        action.setEdit(simpleEdit(context, diagnostic.getRange(), document.getSchemaIdentifier()));
        return action;
    }

	@Override
	public List<Either<Command, CodeAction>> getActions(EventCodeActionContext context) {
        List<Either<Command, CodeAction>> result = new ArrayList<>();

        for (Diagnostic diagnostic : context.diagnostics) {
            if (diagnostic.getCode() == null) {
                // unhandled
                continue;
            }

            if (diagnostic.getCode().isLeft()) continue; // we don't to string codes

            DiagnosticCode code = SchemaDiagnostic.codeFromInt(diagnostic.getCode().getRight());

            switch (code) {
                case SCHEMA_NAME_SAME_AS_FILE:
                    result.add(Either.forRight(schemaNameSameAsFileFix(context, diagnostic)));
                    break;
                case DOCUMENT_NAME_SAME_AS_SCHEMA:
                    result.add(Either.forRight(documentNameSameAsSchemaFix(context, diagnostic)));
                    break;
                default:
                    break;
            }

        }

        return result;
	}
}
