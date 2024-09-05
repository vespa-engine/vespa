package ai.vespa.schemals.lsp.codeaction.provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.common.StringUtils;
import ai.vespa.schemals.context.EventCodeActionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.lsp.codeaction.utils.CodeActionUtils;
import ai.vespa.schemals.lsp.rename.SchemaRename;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ast.NL;
import ai.vespa.schemals.parser.ast.RBRACE;
import ai.vespa.schemals.parser.ast.attributeElm;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.fieldBodyElm;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.indexInsideField;
import ai.vespa.schemals.parser.ast.indexingElm;
import ai.vespa.schemals.parser.ast.inheritsDocument;
import ai.vespa.schemals.parser.ast.inheritsRankProfile;
import ai.vespa.schemals.parser.ast.openLbrace;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.rootSchemaItem;
import ai.vespa.schemals.schemadocument.DocumentManager;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * QuickfixAction
 * Responsible for giving quickfixes for the given code action request
 */
public class QuickFixProvider implements CodeActionProvider {

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
        action.setIsPreferred(true);

        return action;
    }

    private CodeAction fixDocumentNameSameAsSchema(EventCodeActionContext context, Diagnostic diagnostic) {
        if (!(context.document instanceof SchemaDocument)) return null; // basically unreachable
        SchemaDocument document = (SchemaDocument)context.document;
        if (document.getSchemaIdentifier() == null) return null; // quickfix impossible

        CodeAction action = basicQuickFix("Rename document to " + document.getSchemaIdentifier(), diagnostic);
        action.setEdit(CodeActionUtils.simpleEdit(context, diagnostic.getRange(), document.getSchemaIdentifier()));
        action.setIsPreferred(true);

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

    private SchemaNode findRootSchemaItemNode(SchemaNode start) {
        SchemaNode rootSchemaItemNode = start;

        while (rootSchemaItemNode != null) {
            if (rootSchemaItemNode.isASTInstance(rootSchemaItem.class)) break;
            rootSchemaItemNode = rootSchemaItemNode.getParent();
        }
        return rootSchemaItemNode;
    }

    /**
     * Provides quick fix for trying to do dot syntax on a reference field. 
     * @param context
     * @param diagnostic
     * @return A CodeAction which will provide an import field statement and rename the field access.
     */
    private CodeAction fixAccessUnimportedField(EventCodeActionContext context, Diagnostic diagnostic) {
        SchemaNode offendingNode = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);
        if (offendingNode == null) return null;

        SchemaNode referenceFieldNode = offendingNode.getPreviousSibling();

        if (referenceFieldNode == null)return null;
        if (!referenceFieldNode.hasSymbol()) return null;


        String newFieldName = referenceFieldNode.getSymbol().getShortIdentifier() + "_" + offendingNode.getSymbol().getShortIdentifier();

        SchemaNode rootSchemaItemNode = findRootSchemaItemNode(offendingNode);
        if (rootSchemaItemNode == null) return null; // likely inside a rootDocument, where import field is impossible

        Position insertPosition = rootSchemaItemNode.getRange().getEnd();
        String indentString = StringUtils.getIndentString(context.document.getCurrentContent(), rootSchemaItemNode);
        int indent = StringUtils.countSpaceIndents(indentString);

        if (rootSchemaItemNode.getPreviousSibling() != null)insertPosition = rootSchemaItemNode.getPreviousSibling().getRange().getEnd();

        CodeAction action = basicQuickFix("Import field " + offendingNode.getSymbol().getShortIdentifier(), diagnostic);

        action.setEdit(CodeActionUtils.simpleEditList(context, List.of(
            new TextEdit(new Range(insertPosition, insertPosition), 
                StringUtils.spaceIndent(indent) +
                "import field " + referenceFieldNode.getSymbol().getShortIdentifier() + "." + 
                                       offendingNode.getSymbol().getShortIdentifier() + " as " +
                                       newFieldName + " {} \n\n"),
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
        SchemaNode offendingNode = CSTUtils.getNodeAtPosition(context.document.getRootNode(), diagnostic.getRange().getStart());
        String indentString = StringUtils.getIndentString(context.document.getCurrentContent(), offendingNode);
        int indent = StringUtils.countSpaceIndents(indentString) + StringUtils.TAB_SIZE;
        action.setEdit(CodeActionUtils.simpleEditList(context, List.of(
                new TextEdit(new Range(insertPosition, insertPosition),  
                    "\n" + StringUtils.spaceIndent(indent) + "document " + schemaName + " {\n"
                         + StringUtils.spaceIndent(indent + StringUtils.TAB_SIZE) + "\n" + 
                    StringUtils.spaceIndent(indent) + "}")
        )));

        action.setIsPreferred(true);

        return action;
    }

    /**
     * Provides quick fix for 'field foo type reference&lt;document&gt;' and forgot to put indexing: attribute.
     * @param context
     * @param diagnostic
     * @return The CodeAction providing the quick fix.
     */
    private CodeAction fixDocumentReferenceAttribute(EventCodeActionContext context, Diagnostic diagnostic) {
        SchemaNode fieldIdentifierNode = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);
        if (fieldIdentifierNode == null) return null;

        // TODO: maybe append to existing indexing script if possible
        //       Also works poorly for fields outside document, since their indexing scripts require input.
        Position insertPosition = firstPositionInBlock(fieldIdentifierNode.getParent());
        if (insertPosition == null)return null;

        CodeAction action = basicQuickFix("Set indexing to 'attribute' for field " + fieldIdentifierNode.getSymbol().getShortIdentifier(), diagnostic);

        String indentString = StringUtils.getIndentString(context.document.getCurrentContent(),fieldIdentifierNode.getParent());
        int indent = StringUtils.countSpaceIndents(indentString);

        action.setEdit(CodeActionUtils.simpleEdit(context, new Range(insertPosition, insertPosition), 
            "\n" + StringUtils.spaceIndent(indent + StringUtils.TAB_SIZE) + 
            "indexing: attribute\n" + StringUtils.spaceIndent(indent)
        ));

        return action;
    }

    /**
     * Provides quick fix for importing a field that is not an attribute.
     * @param context
     * @param diagnostic
     * @return A CodeAction that will set indexing to attribute for the original field.
     */
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

        String indentString = StringUtils.getIndentString(definitionDocument.getCurrentContent(), definitionFieldElm);
        int indent = StringUtils.countSpaceIndents(indentString);

        action.setEdit(CodeActionUtils.simpleEdit(context, new Range(insertPosition, insertPosition), 
            "\n" + StringUtils.spaceIndent(indent + StringUtils.TAB_SIZE) +
            "indexing: attribute\n" + StringUtils.spaceIndent(indent),
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

        action.setEdit(CodeActionUtils.createInheritsEdit(context, documentIdentifierNode, inheritsDocument.class, schemaReferenceSymbol.getShortIdentifier()));

        action.setIsPreferred(true);

        return action;
    }

    private CodeAction fixInheritsStructFieldRedeclared(EventCodeActionContext context, Diagnostic diagnostic) {
        SchemaNode offendingNode = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);
        if (offendingNode == null) return null;

        CodeAction action = basicQuickFix("Remove field declaration '" + offendingNode.getText() + "'", diagnostic);
        Range fieldRange = offendingNode.getParent().getRange();

        action.setEdit(CodeActionUtils.simpleEdit(context, fieldRange, ""));

        return action;
    }

    private CodeAction fixAnnotationReferenceOutsideAnnotation(EventCodeActionContext context, Diagnostic diagnostic) {
        SchemaNode offendingNode = CSTUtils.getNodeAtPosition(context.document.getRootNode(), context.position);
        if (offendingNode == null) return null;

        SchemaNode fieldElmNode = offendingNode;

        while (fieldElmNode != null && !fieldElmNode.isASTInstance(fieldElm.class)) fieldElmNode = fieldElmNode.getParent();

        if (fieldElmNode == null) return null;

        CodeAction action = basicQuickFix("Enclose field in annotation", diagnostic);

        String fieldBody = fieldElmNode.getText().replace("\n", "\n" + StringUtils.spaceIndent(StringUtils.TAB_SIZE));

        String indentString = StringUtils.getIndentString(context.document.getCurrentContent(), fieldElmNode);
        int indent = StringUtils.countSpaceIndents(indentString);

        action.setEdit(CodeActionUtils.simpleEdit(context, fieldElmNode.getRange(), 
            "annotation myannotation {\n" + 
            StringUtils.spaceIndent(indent + StringUtils.TAB_SIZE) + fieldBody + "\n"
            + StringUtils.spaceIndent(indent) + "}"
        ));

        return action;
    }

    private CodeAction fixDeprecatedArraySyntax(EventCodeActionContext context, Diagnostic diagnostic) {
        SchemaNode tokenNode = CSTUtils.getLeafNodeAtPosition(context.document.getRootNode(), context.position);
        if (tokenNode == null) return null;
        if (!tokenNode.getParent().getParent().isASTInstance(dataType.class))return null;

        String typeText = tokenNode.getText();
        CodeAction action = basicQuickFix("Change to array<" + typeText + ">", diagnostic);

        action.setEdit(CodeActionUtils.simpleEdit(context, tokenNode.getParent().getParent().getRange(), "array<" + typeText + ">"));
        action.setIsPreferred(true);
        return action;
    }

    private CodeAction fixDeprecatedTokenSearch(EventCodeActionContext context, Diagnostic diagnostic) {
        CodeAction action = basicQuickFix("Change to schema", diagnostic);
        action.setEdit(CodeActionUtils.simpleEdit(context, diagnostic.getRange(), "schema"));
        action.setIsPreferred(true);
        return action;
    }

    /**
     * Quick fix for using attribute some_name_other_than_field_name in a field. Will create a new field outside 
     * the document with the specified attribute, using the original field as input and type.
     * @param context
     * @param diagnostic
     * @param code
     * @return CodeAction supplying the edits.
     */
    private CodeAction fixDeprecatedTokenAttributeOrIndex(EventCodeActionContext context, Diagnostic diagnostic, DiagnosticCode code) {
        // TODO: actually create an enclosing schema if it doesn't exist?
        if (!context.document.getRootNode().get(0).isASTInstance(rootSchema.class)) return null;

        Class<? extends Node> offendingBodyClass;
        String replacementKind;

        if (code == DiagnosticCode.DEPRECATED_TOKEN_ATTRIBUTE) {
            offendingBodyClass = attributeElm.class;
            replacementKind = "attribute";
        } else {
            offendingBodyClass = indexInsideField.class;
            replacementKind = "index";
        }

        SchemaNode offendingNode = CSTUtils.getNodeAtPosition(context.document.getRootNode(), context.position);

        // Find the field definition containing the error as well as its data type
        Optional<Symbol> fieldDefinition = CSTUtils.findScope(offendingNode);
        if (fieldDefinition.isEmpty()) return null;

        Optional<SchemaNode> dataTypeNode = context.schemaIndex.fieldIndex().getFieldDataTypeNode(fieldDefinition.get());

        // use some arbitrary default, the generated field will most likely require manual editing anyways
        String dataTypeString = dataTypeNode.isPresent() ? dataTypeNode.get().getText() : "string"; 

        // Find the node containing the entire index or attribute statement
        SchemaNode offendingBodyElmNode = offendingNode;
        while (offendingBodyElmNode != null && !offendingBodyElmNode.isASTInstance(offendingBodyClass)) {
            offendingBodyElmNode = offendingBodyElmNode.getParent();
        }
        if (offendingBodyElmNode == null) return null;

        // Find the rootSchemaItemNode containing the document so we can insert after
        SchemaNode rootSchemaItemNode = findRootSchemaItemNode(offendingNode);
        if (rootSchemaItemNode == null) return null;


        // Calculate required indent and insert position
        String insertIndentString = StringUtils.getIndentString(context.document.getCurrentContent(), rootSchemaItemNode);
        int indent = StringUtils.countSpaceIndents(insertIndentString);
        Range insertPosition = new Range(rootSchemaItemNode.getRange().getEnd(), rootSchemaItemNode.getRange().getEnd());


        // subtract indent from the index/attribute block to match the new indent outside document
        String offendingBodyIndentString = StringUtils.getIndentString(context.document.getCurrentContent(), offendingBodyElmNode);
        int bodyIndentDelta = indent + StringUtils.TAB_SIZE - StringUtils.countSpaceIndents(offendingBodyIndentString);
        String offendingBodyText = StringUtils.addIndents(
            offendingBodyElmNode.getText().replace("\t", StringUtils.spaceIndent(StringUtils.TAB_SIZE)),
            bodyIndentDelta
        );

        CodeAction action = basicQuickFix("Create field '" + offendingNode.getText() + "' outside document", diagnostic);
        action.setEdit(CodeActionUtils.simpleEditList(context, List.of(
            new TextEdit(offendingBodyElmNode.getRange(), ""),
            new TextEdit(insertPosition, "\n" + StringUtils.spaceIndent(indent) +
                "field " + offendingNode.getText() + " type " + dataTypeString + " {\n" +
                StringUtils.spaceIndent(indent + StringUtils.TAB_SIZE) + "# TODO: Auto-generated indexing statement\n" +
                // Fields outside document needs an indexing statement to be useful
                StringUtils.spaceIndent(indent + StringUtils.TAB_SIZE) + "indexing: input " + fieldDefinition.get().getShortIdentifier() + " | " + replacementKind + "\n" +
                StringUtils.spaceIndent(indent + StringUtils.TAB_SIZE) + offendingBodyText + "\n" + 
                StringUtils.spaceIndent(indent) + "}")
        )));

        action.setIsPreferred(true);

        return action;
    }

    private CodeAction fixFeaturesInheritsNonParent(EventCodeActionContext context, Diagnostic diagnostic) {
        SchemaNode offendingNode = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);
        if (offendingNode == null || offendingNode.getSymbol().getScope() == null) return null;

        Symbol rankProfileSymbol = offendingNode.getSymbol().getScope();
        CodeAction action = basicQuickFix("Inherit rank-profile " + offendingNode.getText() + " from " + rankProfileSymbol.getShortIdentifier(), diagnostic);
        action.setEdit(CodeActionUtils.createInheritsEdit(context, rankProfileSymbol.getNode(), inheritsRankProfile.class, offendingNode.getText()));
        return action;
    }

    private CodeAction fixFieldArgumentMissingIndexingType(EventCodeActionContext context, Diagnostic diagnostic) {

        SchemaNode offendingNode = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);
        if (offendingNode == null || !offendingNode.hasSymbol()) return null;
        Optional<Symbol> symbol = context.schemaIndex.findSymbol(offendingNode.getSymbol());
        if (symbol.isEmpty()) return null;

        SchemaNode node = symbol.get().getNode().getParent();

        Optional<SchemaNode> indexingNode = Optional.empty();
        for (SchemaNode child : node) {
            if (child.isASTInstance(fieldBodyElm.class)) {
                SchemaNode grandChild = child.get(0);
                if (grandChild != null && grandChild.isASTInstance(indexingElm.class)) {
                    indexingNode = Optional.of(grandChild);
                }
            }
        }

        CodeAction action = basicQuickFix("Add attribute as indexing attribute", diagnostic);

        if (indexingNode.isPresent()) {
            action.setEdit(CodeActionUtils.simpleEdit(context, indexingNode.get().getRange().getEnd(), " | attribute"));

        } else {
            // Add indexing node as well
            Optional<SchemaNode> rBraceNode = Optional.empty();

            for (int i = 0; i < node.size(); i++) {
                SchemaNode child = node.get(i);
                if (child.isASTInstance(RBRACE.class)) {
                    rBraceNode = Optional.of(child);
                    break;
                }
            }

            if (rBraceNode.isEmpty()) return null;

            // Calculate indent
            SchemaNode fieldKeywordNode = node.get(0);
            if (fieldKeywordNode == null) return null;

            String insertIndentString = StringUtils.getIndentString(context.document.getCurrentContent(), fieldKeywordNode);
            int indent = StringUtils.countSpaceIndents(insertIndentString);

            String newText = StringUtils.spaceIndent(StringUtils.TAB_SIZE) + "indexing: attribute\n" + StringUtils.spaceIndent(indent);

            // Check is a leading new line is necessary
            SchemaNode prevSibling = rBraceNode.get().getPrevious();
            if (prevSibling == null) return null;
            boolean newLineNecessary = false;
            if (!prevSibling.isASTInstance(NL.class)) {
                if (prevSibling.isASTInstance(openLbrace.class)) {
                    SchemaNode lastLbraceChild = prevSibling.get(prevSibling.size() - 1);
                    if (!lastLbraceChild.isASTInstance(NL.class)) {
                        newLineNecessary = true;
                    }
                } else {
                    newLineNecessary = true;
                }
            }

            if (newLineNecessary) {
                newText = "\n" + StringUtils.spaceIndent(indent) + newText;
            }

            action.setEdit(CodeActionUtils.simpleEdit(context, rBraceNode.get().getRange().getStart(), newText));
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
                    break;
                case DOCUMENT_REFERENCE_ATTRIBUTE:
                    result.add(Either.forRight(fixDocumentReferenceAttribute(context, diagnostic)));
                    break;
                case IMPORT_FIELD_ATTRIBUTE:
                    result.add(Either.forRight(fixImportFieldAttribute(context, diagnostic)));
                    break;
                case EXPLICITLY_INHERIT_DOCUMENT:
                    result.add(Either.forRight(fixExplicitlyInheritDocument(context, diagnostic)));
                    break;
                case INHERITS_STRUCT_FIELD_REDECLARED:
                    result.add(Either.forRight(fixInheritsStructFieldRedeclared(context, diagnostic)));
                    break;
                case ANNOTATION_REFERENCE_OUTSIDE_ANNOTATION:
                    result.add(Either.forRight(fixAnnotationReferenceOutsideAnnotation(context, diagnostic)));
                    break;
                case DEPRECATED_ARRAY_SYNTAX:
                    result.add(Either.forRight(fixDeprecatedArraySyntax(context, diagnostic)));
                    break;
                case DEPRECATED_TOKEN_SEARCH:
                    result.add(Either.forRight(fixDeprecatedTokenSearch(context, diagnostic)));
                    break;
                case DEPRECATED_TOKEN_ATTRIBUTE:
                case DEPRECATED_TOKEN_INDEX:
                    result.add(Either.forRight(fixDeprecatedTokenAttributeOrIndex(context, diagnostic, code)));
                    break;
                case FEATURES_INHERITS_NON_PARENT:
                    result.add(Either.forRight(fixFeaturesInheritsNonParent(context, diagnostic)));
                    break;
                case FIELD_ARGUMENT_MISSING_INDEXING_TYPE:
                    result.add(Either.forRight(fixFieldArgumentMissingIndexingType(context, diagnostic)));
                    break;
                default:
                    break;
            }
        }

        return result;
	}
}
