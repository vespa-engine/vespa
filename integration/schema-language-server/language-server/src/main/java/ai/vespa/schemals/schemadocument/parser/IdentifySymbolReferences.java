package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ast.fieldsElm;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.inheritsDocument;
import ai.vespa.schemals.parser.ast.inheritsStruct;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.ast.inheritsRankProfile;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.summaryInDocument;
import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;

public class IdentifySymbolReferences extends Identifier {

    public IdentifySymbolReferences(ParseContext context) {
		super(context);
	}

    private static final HashMap<Class<? extends Node>, SymbolType> identifierTypeMap = new HashMap<Class<? extends Node>, SymbolType>() {{
        put(inheritsDocument.class, SymbolType.DOCUMENT);
        put(fieldsElm.class, SymbolType.FIELD);
        put(rootSchema.class, SymbolType.SCHEMA);
        put(inheritsStruct.class, SymbolType.STRUCT);
    }};

    private static final HashMap<Class<? extends Node>, SymbolType> identifierWithDashTypeMap = new HashMap<Class<? extends Node>, SymbolType>() {{
        put(inheritsRankProfile.class, SymbolType.RANK_PROFILE);
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

        HashMap<Class<? extends Node>, SymbolType> searchMap = isIdentifier ? identifierTypeMap : identifierWithDashTypeMap;
        SymbolType symbolType = searchMap.get(parent.getASTClass());
        if (symbolType == null) return ret;

        node.setSymbol(symbolType, context.fileURI());
        node.setSymbolStatus(SymbolStatus.UNRESOLVED);

        return ret;
    }

    private ArrayList<Diagnostic> identifyRankExpressionLanguage(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        SchemaNode parent = node.getParent();
        var type = node.getRankExpressionType();
        if (parent == null || type == null) return ret;

        boolean isIdentifier = parent.isRankExpressionASTInstance(ai.vespa.schemals.parser.rankingexpression.ast.identifierStr.class);
        if (!isIdentifier || type != ai.vespa.schemals.parser.rankingexpression.Token.TokenType.IDENTIFIER) {
            return ret;
        }

        node.setSymbol(SymbolType.TYPE_UNKNOWN, context.fileURI());
        node.setSymbolStatus(SymbolStatus.UNRESOLVED);

        return ret;
    }
}
