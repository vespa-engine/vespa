package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.FunctionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ast.COLON;
import ai.vespa.schemals.parser.ast.FIELD;
import ai.vespa.schemals.parser.ast.annotationRefDataType;
import ai.vespa.schemals.parser.ast.fieldRankFilter;
import ai.vespa.schemals.parser.ast.fieldRankType;
import ai.vespa.schemals.parser.ast.fieldsElm;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.ast.importField;
import ai.vespa.schemals.parser.ast.inheritsAnnotation;
import ai.vespa.schemals.parser.ast.inheritsDocument;
import ai.vespa.schemals.parser.ast.inheritsDocumentSummary;
import ai.vespa.schemals.parser.ast.inheritsRankProfile;
import ai.vespa.schemals.parser.ast.inheritsStruct;
import ai.vespa.schemals.parser.ast.rankTypeElm;
import ai.vespa.schemals.parser.ast.referenceType;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.structFieldElm;
import ai.vespa.schemals.parser.ast.summaryInDocument;
import ai.vespa.schemals.parser.ast.summaryItem;
import ai.vespa.schemals.parser.ast.summarySourceList;
import ai.vespa.schemals.parser.rankingexpression.ast.BaseNode;
import ai.vespa.schemals.parser.rankingexpression.ast.LBRACE;
import ai.vespa.schemals.parser.rankingexpression.ast.args;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.schemadocument.resolvers.RankExpressionSymbolResolver;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.Node.LanguageType;

/**
 * IdentifySymbolReferences identifies symbols that are not definitions and sets the SchemaNode to contain an unresolved reference symbol
 */
public class IdentifySymbolReferences extends Identifier<SchemaNode> {

    public IdentifySymbolReferences(ParseContext context) {
		super(context);
	}

    private static final HashMap<Class<?>, SymbolType> identifierTypeMap = new HashMap<Class<?>, SymbolType>() {{
        put(inheritsDocument.class, SymbolType.DOCUMENT);
        put(rootSchema.class, SymbolType.SCHEMA);
        put(inheritsStruct.class, SymbolType.STRUCT);
        put(referenceType.class, SymbolType.DOCUMENT);
        put(inheritsAnnotation.class, SymbolType.ANNOTATION);
        put(annotationRefDataType.class, SymbolType.ANNOTATION);
    }};

    private static final HashMap<Class<?>, SymbolType> identifierWithDashTypeMap = new HashMap<Class<?>, SymbolType>() {{
        put(inheritsRankProfile.class, SymbolType.RANK_PROFILE);
        put(inheritsDocumentSummary.class, SymbolType.DOCUMENT_SUMMARY);
    }};

    /** 
     * If the parent of an identifier belongs to one of these classes
     * it will be handled by the {@link IdentifySymbolReferences#handleFieldReference} method.
     *
     * This involves splitting the identifierStr node into several nodes based on the dot-syntax for fields.
     */
    private static final Set<Class<? extends Node>> fieldReferenceIdentifierParents = new HashSet<>() {{
        add(fieldsElm.class);
        add(structFieldElm.class);
        add(summaryInDocument.class);
        add(summarySourceList.class);
        add(fieldRankFilter.class);
        add(fieldRankType.class);
        add(rankTypeElm.class);
    }};

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        if (node.hasSymbol()) return new ArrayList<Diagnostic>();

        if (node.getLanguageType() == LanguageType.SCHEMA || node.getLanguageType() == LanguageType.CUSTOM) {
            return identifySchemaLanguage(node);
        }

        if (node.getLanguageType() == LanguageType.RANK_EXPRESSION) {
            return identifyRankExpressionLanguage(node);
        }

        return new ArrayList<Diagnostic>();
    }

    private ArrayList<Diagnostic> identifySchemaLanguage(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        boolean isIdentifier = node.isSchemaASTInstance(identifierStr.class);
        boolean isIdentifierWithDash = node.isSchemaASTInstance(identifierWithDashStr.class);

        if (!isIdentifier && !isIdentifierWithDash) {
            return ret;
        }

        SchemaNode parent = node.getParent();
        if (parent == null) return ret;

        if (fieldReferenceIdentifierParents.contains(parent.getASTClass())) {
            return handleFieldReference(node);
        }

        if (parent.isASTInstance(importField.class)) {
            return handleImportField(node);
        }

        HashMap<Class<?>, SymbolType> searchMap = isIdentifier ? identifierTypeMap : identifierWithDashTypeMap;
        SymbolType symbolType = searchMap.get(parent.getASTClass());
        if (symbolType == null) return ret;


        Optional<Symbol> scope = CSTUtils.findScope(node);
        if (scope.isPresent()) {
            node.setSymbol(symbolType, context.fileURI(), scope.get());
        } else {
            node.setSymbol(symbolType, context.fileURI());
        }
        node.setSymbolStatus(SymbolStatus.UNRESOLVED);

        if (parent.isSchemaASTInstance(referenceType.class)) {
            context.addUnresolvedDocumentReferenceNode(node);
        }

        return ret;
    }

    private static final Set<Class<?>> identifierNodes = new HashSet<>() {{
        add(ai.vespa.schemals.parser.rankingexpression.ast.identifierStr.class);
        addAll(RankExpressionSymbolResolver.builtInTokenizedFunctions);
    }};

    private ArrayList<Diagnostic> identifyRankExpressionLanguage(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();
        if (!identifierNodes.contains(node.getOriginalRankExpressionNode().getClass())) return ret;

        SchemaNode parent = node.getParent();


        if (!(parent.getOriginalRankExpressionNode() instanceof BaseNode)) return ret;

        ExpressionNode expressionNode = ((BaseNode)parent.getOriginalRankExpressionNode()).expressionNode;

        if (expressionNode instanceof ReferenceNode) {

            SymbolType type = SymbolType.TYPE_UNKNOWN;

            Reference reference = ((ReferenceNode)expressionNode).reference();
            String referenceTo = reference.name();

            if (!reference.isIdentifier())type = SymbolType.FUNCTION;


            Optional<Symbol> scope = CSTUtils.findScope(node);

            if (referenceTo.equals("file")) {
                // TODO: handle expression files;
                return ret;
            }

            if (scope.isPresent()) {
                node.setSymbol(type, context.fileURI(), scope.get(), referenceTo);
            } else {
                node.setSymbol(type, context.fileURI(), null, referenceTo);
            }
            node.setSymbolStatus(SymbolStatus.UNRESOLVED);
        } else if (parent.getOriginalRankExpressionNode() instanceof BaseNode 
            && ((BaseNode)parent.getOriginalRankExpressionNode()).expressionNode instanceof FunctionNode) {
            // This might be a function reference
            Optional<Symbol> scope = CSTUtils.findScope(node);
            if (scope.isPresent()) {
                node.setSymbol(SymbolType.FUNCTION, context.fileURI(), scope.get(), node.getText());
            } else {
                node.setSymbol(SymbolType.FUNCTION, context.fileURI(), null, node.getText());
            }
            node.setSymbolStatus(SymbolStatus.UNRESOLVED);
        } else if (parent.getIsDirty() && node.getNextSibling().isASTInstance(LBRACE.class) && node.getNextSibling().getNextSibling().isASTInstance(args.class)) {
            // This case happens if you write feature(). (with nothing after the dot). 
            // Parser marks the parent as dirty, so it doesnt receive a ReferenceNode
            Optional<Symbol> scope = CSTUtils.findScope(node);

            if (scope.isPresent()) {
                node.setSymbol(SymbolType.FUNCTION, context.fileURI(), scope.get());
            } else {
                node.setSymbol(SymbolType.FUNCTION, context.fileURI());
            }
        }
        return ret;
    }
    
    private ArrayList<Diagnostic> handleFieldReference(SchemaNode identifierNode) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        SchemaNode parent = identifierNode.getParent();

        // Edge case: if we are in a summary element and there is specified a source list, we will not mark it as a reference
        if (parent.isASTInstance(summaryInDocument.class) && summaryHasSourceList(parent)) {
            return ret;
        }

        // Another edge case: if someone writes summary documentid {}
        if ((parent.isASTInstance(summaryInDocument.class) || parent.isASTInstance(summarySourceList.class))
            && identifierNode.getText().equals("documentid")) {
            /*
             * TODO: this actually doesn't work when you deploy if parent is summarySourceList and 
             *       you have imported a field for some reason. It would be helpful to show a nice message to the user.
             */
            Optional<Symbol> scope = CSTUtils.findScope(identifierNode);
            if (scope.isPresent()) {
                identifierNode.setSymbol(SymbolType.FIELD, context.fileURI(), scope.get());
            } else {
                identifierNode.setSymbol(SymbolType.FIELD, context.fileURI());
            }
            identifierNode.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
            return ret;
        }

        // Another edge case: rank-type ...
        if ((parent.isASTInstance(fieldRankType.class) || parent.isASTInstance(rankTypeElm.class)) 
                && identifierNode.getPreviousSibling() != null  
                && identifierNode.getPreviousSibling().isASTInstance(COLON.class)) {
            List<String> validRankTypes = new ArrayList<>() {{
                add("identity");
                add("about");
                add("tags");
                add("empty");
                add("default"); // sneaky valid rank-type
            }};

            if (!validRankTypes.contains(identifierNode.getText())) {
                String msg = "Invalid rank type. Supported rank types are ";
                for (int i = 0; i < validRankTypes.size(); ++i) {
                    msg += "'" + validRankTypes.get(i) + "'";
                    if (i < validRankTypes.size() - 1) msg += ", ";
                }
                msg += ".";
                ret.add(new SchemaDiagnostic.Builder()
                    .setRange(identifierNode.getRange())
                    .setMessage(msg)
                    .setSeverity(DiagnosticSeverity.Error)
                    .build());
            }
            return ret;
        }

        String fieldIdentifier = identifierNode.getText();

        String[] subfields = fieldIdentifier.split("[.]");

        int newStart = identifierNode.getRange().getStart().getCharacter();

        // First item in the list should be of type field
        int newEnd = newStart + subfields[0].length();

        identifierNode.setNewEndCharacter(newEnd);

        if (identifierNode.size() != 0) {
            identifierNode.get(0).setNewEndCharacter(newEnd);
        }

        Optional<Symbol> scope = CSTUtils.findScope(identifierNode);

        SymbolType firstType = SymbolType.FIELD;

        if (parent.isASTInstance(structFieldElm.class)) {
            firstType = SymbolType.SUBFIELD;
        }

        if (scope.isPresent()) {
            identifierNode.setSymbol(firstType, context.fileURI(), scope.get());
        } else {
            identifierNode.setSymbol(firstType, context.fileURI());
        }
        identifierNode.setSymbolStatus(SymbolStatus.UNRESOLVED);

        int myIndex = parent.indexOf(identifierNode);
        for (int i = 1; i < subfields.length; ++i) {
            newStart += subfields[i-1].length() + 1; // +1 for the dot
            newEnd += subfields[i].length() + 1;

            identifierStr newASTNode = new identifierStr();
            newASTNode.setTokenSource(identifierNode.getTokenSource());
            newASTNode.setBeginOffset(identifierNode.getOriginalSchemaNode().getBeginOffset());
            newASTNode.setEndOffset(identifierNode.getOriginalSchemaNode().getEndOffset());

            SchemaNode newNode = new SchemaNode(newASTNode);
            newNode.setNewStartCharacter(newStart);
            newNode.setNewEndCharacter(newEnd);
            parent.insertChildAfter(myIndex, newNode);

            scope = CSTUtils.findScope(newNode);

            if (scope.isPresent()) {
                newNode.setSymbol(SymbolType.SUBFIELD, context.fileURI(), scope.get());
            } else {
                newNode.setSymbolStatus(SymbolStatus.UNRESOLVED);
            }

            myIndex++;
        }

        return ret;
    }

    /*
     * This needs to split the AST node containing a dot into two nodes
     */
    private ArrayList<Diagnostic> handleImportField(SchemaNode identifierNode) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!identifierNode.getPreviousSibling().isASTInstance(FIELD.class)) return ret;

        SchemaNode parent = identifierNode.getParent();

        String fieldIdentifier = identifierNode.getText();

        if (!fieldIdentifier.contains(".")) {
            // TODO: parser throws an error here. But we could handle it so it looks better
            return ret;
        }

        if (fieldIdentifier.endsWith(".") || fieldIdentifier.startsWith(".")) {
            ret.add(new SchemaDiagnostic.Builder()
                    .setRange( identifierNode.getRange())
                    .setMessage( "Expected an identifier")
                    .setSeverity( DiagnosticSeverity.Error)
                    .build() );
            return ret;
        }

        String[] subfields = fieldIdentifier.split("[.]");

        int newStart = identifierNode.getRange().getStart().getCharacter();
        int newEnd = newStart + subfields[0].length();
        identifierNode.setNewEndCharacter(newEnd);

        // Set new end for the token inside this node
        if (identifierNode.size() != 0) {
            identifierNode.get(0).setNewEndCharacter(newEnd);
        }

        Optional<Symbol> scope = CSTUtils.findScope(identifierNode);

        if (scope.isPresent()) {
            identifierNode.setSymbol(SymbolType.FIELD, context.fileURI(), scope.get());
        } else {
            identifierNode.setSymbol(SymbolType.FIELD, context.fileURI());
        }
        identifierNode.setSymbolStatus(SymbolStatus.UNRESOLVED);


        // Construct a new node which will be a reference to the subfield
        int myIndex = parent.indexOf(identifierNode);

        newStart += subfields[0].length() + 1; // +1 for the dot
        newEnd += subfields[1].length() + 1;

        identifierStr newASTNode = new identifierStr();
        newASTNode.setTokenSource(identifierNode.getTokenSource());
        newASTNode.setBeginOffset(identifierNode.getOriginalSchemaNode().getBeginOffset());
        newASTNode.setEndOffset(identifierNode.getOriginalSchemaNode().getEndOffset());

        SchemaNode newNode = new SchemaNode(newASTNode);
        newNode.setNewStartCharacter(newStart);
        newNode.setNewEndCharacter(newEnd);

        parent.insertChildAfter(myIndex, newNode);

        scope = CSTUtils.findScope(newNode);

        if (scope.isPresent()) {
            newNode.setSymbol(SymbolType.SUBFIELD, context.fileURI(), scope.get());
        } else {
            newNode.setSymbol(SymbolType.SUBFIELD, context.fileURI());
        }

        return ret;
    }

    /*
     * Given a summary node e.g. summaryInDocument, return true if the summary has a source list.
     */
    private boolean summaryHasSourceList(SchemaNode summaryNode) {
        for (SchemaNode child : summaryNode) {
            if (child.isASTInstance(summaryItem.class) && child.get(0).isASTInstance(summarySourceList.class)) return true;
        }
        return false;
    }

}
