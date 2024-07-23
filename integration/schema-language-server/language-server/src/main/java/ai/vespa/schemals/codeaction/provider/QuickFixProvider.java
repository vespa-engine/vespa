package ai.vespa.schemals.codeaction.provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.util.Positions;

import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.EventCodeActionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.parser.ast.rootSchemaItem;
import ai.vespa.schemals.rename.SchemaRename;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * QuickfixAction
 * Responsible for giving quickfixes for the given code action request
 */
public class QuickFixProvider implements CodeActionProvider {

    private WorkspaceEdit simpleEditList(EventCodeActionContext context, List<TextEdit> edits) {
        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        TextDocumentEdit textDocumentEdit = new TextDocumentEdit();
        textDocumentEdit.setTextDocument(context.document.getVersionedTextDocumentIdentifier());
        textDocumentEdit.setEdits(List.copyOf(edits));
        workspaceEdit.setDocumentChanges(List.of(Either.forLeft(textDocumentEdit)));
        return workspaceEdit;
    }

    private WorkspaceEdit simpleEdit(EventCodeActionContext context, Range range, String newText) {
        return simpleEditList(context, List.of(new TextEdit(range, newText)));
    }

    private CodeAction basicQuickFix(String title, Diagnostic fixFor) {
        CodeAction action = new CodeAction();
        action.setTitle(title);
        action.setKind(CodeActionKind.QuickFix);
        action.setDiagnostics(List.of(fixFor));
        return action;
    }

    private CodeAction fixSchemaNameSameAsFile(EventCodeActionContext context, Diagnostic diagnostic) {
        String requiredName = FileUtils.schemaNameFromPath(context.document.getFileURI());
        CodeAction action = basicQuickFix("Rename schema to " + requiredName,  diagnostic);
        action.setEdit(SchemaRename.rename(context, requiredName));
        return action;
    }

    private CodeAction fixDocumentNameSameAsSchema(EventCodeActionContext context, Diagnostic diagnostic) {
        if (!(context.document instanceof SchemaDocument)) return null; // basically unreachable
        SchemaDocument document = (SchemaDocument)context.document;
        if (document.getSchemaIdentifier() == null) return null; // quickfix impossible

        CodeAction action = basicQuickFix("Rename document to " + document.getSchemaIdentifier(), diagnostic);
        action.setEdit(simpleEdit(context, diagnostic.getRange(), document.getSchemaIdentifier()));
        return action;
    }

    private CodeAction fixAccessUnimportedField(EventCodeActionContext context, Diagnostic diagnostic) {
        SchemaNode offendingNode = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);
        if (offendingNode == null) return null;

        SchemaNode referenceFieldNode = offendingNode.getPreviousSibling();

        if (referenceFieldNode == null)return null;
        if (!referenceFieldNode.hasSymbol()) return null;


        String newFieldName = referenceFieldNode.getSymbol().getShortIdentifier() + "_" + offendingNode.getSymbol().getShortIdentifier();

        SchemaNode rootSchemaItemNode = offendingNode;

        while (rootSchemaItemNode != null) {
            if (rootSchemaItemNode.isASTInstance(rootSchemaItem.class)) break;
            rootSchemaItemNode = rootSchemaItemNode.getParent();
        }
        if (rootSchemaItemNode == null) return null; // likely inside a rootDocument, where import field is impossible

        Position insertPosition = rootSchemaItemNode.getRange().getEnd();
        int indent = rootSchemaItemNode.getRange().getStart().getCharacter();


        if (rootSchemaItemNode.getPreviousSibling() != null)insertPosition = rootSchemaItemNode.getPreviousSibling().getRange().getEnd();

        CodeAction action = basicQuickFix("Import field " + offendingNode.getSymbol().getShortIdentifier(), diagnostic);

        insertPosition.setCharacter(indent);

        action.setEdit(simpleEditList(context, List.of(
            new TextEdit(new Range(insertPosition, insertPosition), 
                "import field " + referenceFieldNode.getSymbol().getShortIdentifier() + "." + 
                                       offendingNode.getSymbol().getShortIdentifier() + " as " +
                                       newFieldName + " {} \n\n" + new String(new char[indent]).replace("\0", " ")),
            new TextEdit(CSTUtils.unionRanges(offendingNode.getRange(), referenceFieldNode.getRange()), newFieldName)
        )));

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
                    result.add(Either.forRight(fixSchemaNameSameAsFile(context, diagnostic)));
                    break;
                case DOCUMENT_NAME_SAME_AS_SCHEMA:
                    result.add(Either.forRight(fixDocumentNameSameAsSchema(context, diagnostic)));
                    break;
                case ACCESS_UNIMPORTED_FIELD:
                    result.add(Either.forRight(fixAccessUnimportedField(context, diagnostic)));
                    break;
                default:
                    break;
            }

        }

        return result;
	}
}
