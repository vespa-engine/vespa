package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import com.google.protobuf.Option;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ast.fieldsElm;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.inheritsDocument;
import ai.vespa.schemals.parser.ast.inheritsDocumentSummary;
import ai.vespa.schemals.parser.ast.inheritsStruct;
import ai.vespa.schemals.parser.ast.rankProfile;
import ai.vespa.schemals.parser.ast.referenceType;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.ast.inheritsRankProfile;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;

public class IdentifySymbolReferences extends Identifier {

    public IdentifySymbolReferences(ParseContext context) {
		super(context);
	}

    private static final HashMap<Class<?>, SymbolType> identifierTypeMap = new HashMap<Class<?>, SymbolType>() {{
        put(inheritsDocument.class, SymbolType.DOCUMENT);
        put(fieldsElm.class, SymbolType.FIELD);
        put(rootSchema.class, SymbolType.SCHEMA);
        put(inheritsStruct.class, SymbolType.STRUCT);
        put(referenceType.class, SymbolType.DOCUMENT);
    }};

    private static final HashMap<Class<?>, SymbolType> identifierWithDashTypeMap = new HashMap<Class<?>, SymbolType>() {{
        put(inheritsRankProfile.class, SymbolType.RANK_PROFILE);
        put(inheritsDocumentSummary.class, SymbolType.DOCUMENT_SUMMARY);
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

        HashMap<Class<?>, SymbolType> searchMap = isIdentifier ? identifierTypeMap : identifierWithDashTypeMap;
        SymbolType symbolType = searchMap.get(parent.getASTClass());
        if (symbolType == null) return ret;

        if (parent.isASTInstance(fieldsElm.class)) {
            return handleFields(node);
        }

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

    private ArrayList<Diagnostic> identifyRankExpressionLanguage(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (node.size() == 0) return ret;

        SchemaNode child = node.get(0);
        var type = child.getRankExpressionType();
        boolean isIdentifier = node.isRankExpressionASTInstance(ai.vespa.schemals.parser.rankingexpression.ast.identifierStr.class);

        if (!isIdentifier || type != ai.vespa.schemals.parser.rankingexpression.Token.TokenType.IDENTIFIER) {
            return ret;
        }

        Optional<Symbol> scope = CSTUtils.findScope(node);

        if (scope.isPresent()) {
            node.setSymbol(SymbolType.TYPE_UNKNOWN, context.fileURI(), scope.get());
        } else {
            node.setSymbol(SymbolType.TYPE_UNKNOWN, context.fileURI());
        }
        
        node.setSymbolStatus(SymbolStatus.UNRESOLVED);

        return ret;
    }
    
    private ArrayList<Diagnostic> handleFields(SchemaNode identifierNode) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        SchemaNode parent = identifierNode.getParent();

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
        if (scope.isPresent()) {
            identifierNode.setSymbol(SymbolType.FIELD, context.fileURI(), scope.get());
        } else {
            identifierNode.setSymbol(SymbolType.FIELD, context.fileURI());
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
}
