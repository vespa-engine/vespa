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
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.fieldBodyElm;
import ai.vespa.schemals.parser.ast.indexingElm;
import ai.vespa.schemals.parser.ast.inheritsDocument;
import ai.vespa.schemals.parser.ast.openLbrace;
import ai.vespa.schemals.parser.ast.rootSchemaItem;
import ai.vespa.schemals.rename.SchemaRename;
import ai.vespa.schemals.schemadocument.DocumentManager;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * QuickfixAction
 * Responsible for giving quickfixes for the given code action request
 */
public class QuickFixProvider implements CodeActionProvider {

    private WorkspaceEdit simpleEditList(EventCodeActionContext context, List<TextEdit> edits, DocumentManager document) {
        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        TextDocumentEdit textDocumentEdit = new TextDocumentEdit();
        textDocumentEdit.setTextDocument(document.getVersionedTextDocumentIdentifier());
        textDocumentEdit.setEdits(edits.stream().filter(edit -> edit != null).toList());
        workspaceEdit.setDocumentChanges(List.of(Either.forLeft(textDocumentEdit)));
        return workspaceEdit;
    }

    private WorkspaceEdit simpleEditList(EventCodeActionContext context, List<TextEdit> edits) {
        return simpleEditList(context, edits, context.document);
    }

    private WorkspaceEdit simpleEdit(EventCodeActionContext context, Range range, String newText) {
        return simpleEditList(context, List.of(new TextEdit(range, newText)));
    }

    private WorkspaceEdit simpleEdit(EventCodeActionContext context, Range range, String newText, DocumentManager document) {
        return simpleEditList(context, List.of(new TextEdit(range, newText)), document);
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

    private Position firstPositionInBlock(SchemaNode blockNode) {
        for (SchemaNode child : blockNode) {
            if (child.isASTInstance(openLbrace.class)) {
                return child.get(0).getRange().getEnd();
            }
        }
        return null;
    }

    private String spaceIndent(int indent) {
        return new String(new char[indent]).replace("\0", " ");
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
                                       newFieldName + " {} \n\n" + spaceIndent(indent)),
            new TextEdit(CSTUtils.unionRanges(offendingNode.getRange(), referenceFieldNode.getRange()), newFieldName)
        )));

        return action;
    }

    private CodeAction fixDocumentlessSchema(EventCodeActionContext context, Diagnostic diagnostic) {
        if (!(context.document instanceof SchemaDocument)) return null; // basically unreachable
        SchemaDocument document = (SchemaDocument)context.document;
        if (document.getSchemaIdentifier() == null) return null; // quickfix impossible
        String schemaName = document.getSchemaIdentifier();
        CodeAction action = basicQuickFix("Insert document definition '" + schemaName + "'", diagnostic);

        SchemaNode rootSchemaNode = document.getRootNode().get(0);
        Position insertPosition = firstPositionInBlock(rootSchemaNode);

        if (insertPosition == null) {
            insertPosition = CSTUtils.addPositions(diagnostic.getRange().getStart(), new Position(1, 0)); // one line below
            insertPosition.setCharacter(0);
        }
        int indent = diagnostic.getRange().getStart().getCharacter() + 4;
        action.setEdit(simpleEditList(context, List.of(
                new TextEdit(new Range(insertPosition, insertPosition),  
                    "\n" + spaceIndent(indent) + "document " + schemaName + " {\n" + 
                        spaceIndent(indent + 4) + "\n" + 
                    spaceIndent(indent) + "}\n")
        )));
        return action;
    }
    private CodeAction fixDocumentReferenceAttribute(EventCodeActionContext context, Diagnostic diagnostic) {
        SchemaNode fieldIdentifierNode = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);
        if (fieldIdentifierNode == null) return null;

        // TODO: maybe append to existing indexing script if possible
        //       Also works poorly for fields outside document, since their indexing scripts require input.
        Position insertPosition = firstPositionInBlock(fieldIdentifierNode.getParent());
        if (insertPosition == null)return null;

        CodeAction action = basicQuickFix("Set indexing to 'attribute' for field " + fieldIdentifierNode.getSymbol().getShortIdentifier(), diagnostic);

        int indent = fieldIdentifierNode.getParent().getRange().getStart().getCharacter() + 4;

        action.setEdit(simpleEdit(context, new Range(insertPosition, insertPosition), 
            "\n" + spaceIndent(indent) + "indexing: attribute\n" + spaceIndent(indent - 4)
        ));

        return action;
    }

    private CodeAction fixImportFieldAttribute(EventCodeActionContext context, Diagnostic diagnostic) {

        SchemaNode importReferenceNode = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);
        if (importReferenceNode == null) return null;

        Symbol referenceSymbol = importReferenceNode.getSymbol();

        Optional<Symbol> definitionSymbol = context.schemaIndex.getSymbolDefinition(referenceSymbol);

        if (definitionSymbol.isEmpty()) return null;

        SchemaNode definitionFieldElm = definitionSymbol.get().getNode().getParent();

        DocumentManager definitionDocument = context.scheduler.getDocument(definitionSymbol.get().getFileURI());

        Position insertPosition = firstPositionInBlock(definitionFieldElm);
        CodeAction action = basicQuickFix("Set indexing to 'attribute' for field " + definitionSymbol.get().getLongIdentifier(), diagnostic);

        int indent = definitionFieldElm.getRange().getStart().getCharacter() + 4;

        action.setEdit(simpleEdit(context, new Range(insertPosition, insertPosition), 
            "\n" + spaceIndent(indent) + "indexing: attribute\n" + spaceIndent(indent - 4),
            definitionDocument
        ));

        return action;
    }

    private CodeAction fixExplicitlyInheritDocument(EventCodeActionContext context, Diagnostic diagnostic) {
        SchemaNode offendingNode = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);
        if (offendingNode == null) return null;

        Symbol schemaReferenceSymbol = offendingNode.getSymbol();

        String mySchemaIdentifier = offendingNode.getParent().get(1).getText();

        Optional<Symbol> myDocumentDefinition = context.schemaIndex.findSymbol(null, SymbolType.DOCUMENT, mySchemaIdentifier);

        // findSymbol on DOCUMENT may return SCHEMA. In that case we are not interested
        if (myDocumentDefinition.isEmpty() || myDocumentDefinition.get().getType() != SymbolType.DOCUMENT) return null;

        SchemaNode documentIdentifierNode = myDocumentDefinition.get().getNode();

        CodeAction action = basicQuickFix("Inherit document '" + schemaReferenceSymbol.getShortIdentifier() + "'", diagnostic);

        if (documentIdentifierNode.getNextSibling() != null && documentIdentifierNode.getNextSibling().isASTInstance(inheritsDocument.class)) {
            Position insertPosition = documentIdentifierNode.getNextSibling().getRange().getEnd();
            action.setEdit(simpleEdit(context, new Range(insertPosition, insertPosition), ", " + schemaReferenceSymbol.getShortIdentifier()));
        } else {
            Position insertPosition = documentIdentifierNode.getRange().getEnd();
            action.setEdit(simpleEdit(context, new Range(insertPosition, insertPosition), " inherits " + schemaReferenceSymbol.getShortIdentifier()));
        }

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
                case DOCUMENTLESS_SCHEMA:
                    result.add(Either.forRight(fixDocumentlessSchema(context, diagnostic)));
                case DOCUMENT_REFERENCE_ATTRIBUTE:
                    result.add(Either.forRight(fixDocumentReferenceAttribute(context, diagnostic)));
                case IMPORT_FIELD_ATTRIBUTE:
                    result.add(Either.forRight(fixImportFieldAttribute(context, diagnostic)));
                case EXPLICITLY_INHERIT_DOCUMENT:
                    result.add(Either.forRight(fixExplicitlyInheritDocument(context, diagnostic)));
                default:
                    break;
            }

        }

        return result;
	}
}
