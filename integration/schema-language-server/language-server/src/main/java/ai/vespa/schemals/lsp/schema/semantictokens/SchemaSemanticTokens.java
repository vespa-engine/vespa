package ai.vespa.schemals.lsp.schema.semantictokens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;

import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.Node.LanguageType;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.EventDocumentContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.lsp.common.semantictokens.CommonSemanticTokens;
import ai.vespa.schemals.lsp.common.semantictokens.SemanticTokenMarker;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.TokenSource;
import ai.vespa.schemals.parser.ast.FILTER;
import ai.vespa.schemals.parser.ast.RANK_TYPE;
import ai.vespa.schemals.parser.ast.bool;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.fieldRankType;
import ai.vespa.schemals.parser.ast.integerElm;
import ai.vespa.schemals.parser.ast.matchItem;
import ai.vespa.schemals.parser.ast.matchType;
import ai.vespa.schemals.parser.ast.quotedString;
import ai.vespa.schemals.parser.ast.rankSettingElm;
import ai.vespa.schemals.parser.ast.rankTypeElm;
import ai.vespa.schemals.parser.ast.summaryItem;
import ai.vespa.schemals.parser.ast.valueType;

/**
 * Responsible for LSP textDocument/semanticTokens/full requests.
 */
public class SchemaSemanticTokens {

    private static Map<SymbolType, Integer> identifierTypeMap;

    private static Map<TokenType, Integer> schemaTokenTypeMap;
    private static Map<ai.vespa.schemals.parser.rankingexpression.Token.TokenType, Integer> rankExpressionTokenTypeMap;

    public static void init() {

        // Manually added semantic tokens
        CommonSemanticTokens.addTokenTypes(SchemaSemanticTokenConfig.manuallyRegisteredLSPNames);
        int keywordIndex = CommonSemanticTokens.addTokenType(SemanticTokenTypes.Keyword);

        // Add symbol semantic tokens
        identifierTypeMap = new HashMap<SymbolType, Integer>();

        for (Map.Entry<SymbolType, String> set : SchemaSemanticTokenConfig.identifierTypeLSPNameMap.entrySet()) {
            int index = CommonSemanticTokens.addTokenType(set.getValue());
            identifierTypeMap.put(set.getKey(), index);
        }

        // Create Map for Schema Tokens
        schemaTokenTypeMap = new HashMap<TokenType, Integer>();

        for (var set : SchemaSemanticTokenConfig.schemaTokenTypeLSPNameMap.entrySet()) {
            int index = CommonSemanticTokens.addTokenType(set.getValue());
            schemaTokenTypeMap.put(set.getKey(), index);
        }
        
        for (TokenType type : SchemaSemanticTokenConfig.keywordTokens) {
            schemaTokenTypeMap.put(type, keywordIndex);
        }

        // Create Map for RankExpression Tokens
        rankExpressionTokenTypeMap = new HashMap<ai.vespa.schemals.parser.rankingexpression.Token.TokenType, Integer>();

        for (var set : SchemaSemanticTokenConfig.rankingExpressionTokenTypeLSPNameMap.entrySet()) {
            int index = CommonSemanticTokens.addTokenType(set.getValue());
            rankExpressionTokenTypeMap.put(set.getKey(), index);
        }

        for (var type : SchemaSemanticTokenConfig.rankingExpressionKeywordTokens) {
            rankExpressionTokenTypeMap.put(type, keywordIndex);
        }

        int operationIndex = CommonSemanticTokens.addTokenType(SemanticTokenTypes.Operator);
        for (var type : SchemaSemanticTokenConfig.rankingExpressionOperationTokens) {
            rankExpressionTokenTypeMap.put(type, operationIndex);
        }

        int functionIndex = CommonSemanticTokens.addTokenType(SemanticTokenTypes.Function);
        for (var type : SchemaSemanticTokenConfig.rankingExpressioFunctionTokens) {
            rankExpressionTokenTypeMap.put(type, functionIndex);
        }

        CommonSemanticTokens.addTokenModifiers(SchemaSemanticTokenConfig.tokenModifiers);
    }

    private static ArrayList<SemanticTokenMarker> traverseCST(SchemaNode node, ClientLogger logger) {
        ArrayList<SemanticTokenMarker> ret = new ArrayList<SemanticTokenMarker>();

        TokenType schemaType = node.getSchemaType();
        var rankExpressionType = node.getRankExpressionType();
        var indexinglanguageType = node.getIndexingLanguageType();

        // TODO: this became a bit ugly with the map stuff
        if (node.isASTInstance(dataType.class) && (!node.hasSymbol() || node.getSymbol().getType() == SymbolType.MAP_KEY || node.getSymbol().getType() == SymbolType.MAP_VALUE)) {

            Integer tokenType = CommonSemanticTokens.getType("type");
            if (tokenType != -1) {
                // this will leave <> uncolored, as we are only interested in marking the actual token
                Range markerRange = node.findFirstLeaf().getRange();
                ret.add(new SemanticTokenMarker(tokenType, markerRange));
            }
            for (Node child : node) {
                ArrayList<SemanticTokenMarker> markers = traverseCST(child.getSchemaNode(), logger);
                ret.addAll(markers);
            }

        } else if (node.isASTInstance(valueType.class)) {

            Integer tokenType = CommonSemanticTokens.getType("type");
            if (tokenType != -1) {
                ret.add(new SemanticTokenMarker(tokenType, node));
            }
        } else if (node.hasSymbol()) {
            ret.addAll(findSemanticMarkersForSymbol(node));
        } else if (schemaType != null) {
            Integer tokenType = null;
            String modifier = null;
            if (isEnumLike(node)) {
                tokenType = CommonSemanticTokens.getType(SemanticTokenTypes.Property); 
                modifier = SemanticTokenModifiers.Readonly;
            }
            if (tokenType == null) {
                tokenType = schemaTokenTypeMap.get(schemaType);
            }

            if (tokenType != null) {
                var marker =new SemanticTokenMarker(tokenType, node);
                if (modifier != null)marker.addModifier(modifier);
                ret.add(marker);
            }

        } else if (rankExpressionType != null) {
            Integer tokenType = rankExpressionTokenTypeMap.get(rankExpressionType);
            if (tokenType != null) {
                ret.add(new SemanticTokenMarker(tokenType, node));
            }

        } else if (indexinglanguageType != null) {
            if (SchemaSemanticTokenConfig.indexingLanguageOutputs.contains(indexinglanguageType)) {
                Integer tokenType = CommonSemanticTokens.getType("type");
                Range markerRange = node.findFirstLeaf().getRange();
                ret.add(new SemanticTokenMarker(tokenType, markerRange));
            } else if (SchemaSemanticTokenConfig.indexingLanguageKeywords.contains(indexinglanguageType)) {
                Integer tokenType = CommonSemanticTokens.getType("keyword");
                Range markerRange = node.findFirstLeaf().getRange();
                ret.add(new SemanticTokenMarker(tokenType, markerRange));
            } else if (SchemaSemanticTokenConfig.indexingLanguageOperators.contains(indexinglanguageType)) {
                Integer tokenType = CommonSemanticTokens.getType("function");
                Range markerRange = node.findFirstLeaf().getRange();
                ret.add(new SemanticTokenMarker(tokenType, markerRange));
            } else if (indexinglanguageType == ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.STRING) {
                Integer tokenType = CommonSemanticTokens.getType("string");
                Range markerRange = node.findFirstLeaf().getRange();
                ret.add(new SemanticTokenMarker(tokenType, markerRange));
            }
        }  else {
            
            for (Node child : node) {
                ArrayList<SemanticTokenMarker> markers = traverseCST(child.getSchemaNode(), logger);
                ret.addAll(markers);
            }
        }


        return ret;
    }

    private static final Set<Class<?>> enumLikeItems = new HashSet<>() {{
        add(matchType.class);
        add(matchItem.class);
        add(rankSettingElm.class);
        add(fieldRankType.class);
        add(rankTypeElm.class);
        add(summaryItem.class);
    }};

    private static boolean isEnumLike(SchemaNode node) {
        if (node.getLanguageType() != LanguageType.SCHEMA) return false;
        if (node.getParent() == null) return false;
        if (node.getParent().getASTClass() == integerElm.class || node.getParent().getASTClass() == quotedString.class || node.getParent().getASTClass() == bool.class) return false;
        if (!node.isLeaf()) return false;

        // ugly special case
        if (node.isASTInstance(FILTER.class)) return true;

        if (node.isASTInstance(RANK_TYPE.class)) return false;

        Node it = node.getParent();
        while (it != null) {
            if (enumLikeItems.contains(it.getASTClass())) break;
            it = it.getParent();
        }
        if (it == null) return false;

        if (node.getParent().indexOf(node) != 0) return false;
        return true;
    }

    private static List<SemanticTokenMarker> findSemanticMarkersForSymbol(SchemaNode node) {
        List<SemanticTokenMarker> ret = new ArrayList<>();

        if (!node.hasSymbol()) return ret;

        if (SchemaSemanticTokenConfig.userDefinedSymbolTypes.contains(node.getSymbol().getType()) && (
            node.getSymbol().getStatus() == SymbolStatus.REFERENCE ||
            node.getSymbol().getStatus() == SymbolStatus.DEFINITION
        )) {
            Integer tokenType = identifierTypeMap.get(node.getSymbol().getType());
            
            if (tokenType != null) {
                SemanticTokenMarker tokenMarker = new SemanticTokenMarker(tokenType, node);
                if (node.getSymbol().getStatus() == SymbolStatus.DEFINITION) {
                    tokenMarker.addModifier(SemanticTokenModifiers.Definition);
                }
                ret.add(tokenMarker);
            }

        } else if (node.hasSymbol() && node.getSymbol().getStatus() == SymbolStatus.BUILTIN_REFERENCE) {
            SymbolType type = node.getSymbol().getType();
            Integer tokenType = identifierTypeMap.get(type);

            if (type == SymbolType.FUNCTION && node.getLanguageType() == LanguageType.RANK_EXPRESSION) {
                tokenType = CommonSemanticTokens.getType("macro");
            }

            if (tokenType != null && tokenType != -1) {
                SemanticTokenMarker tokenMarker = new SemanticTokenMarker(tokenType, node);
                tokenMarker.addModifier(SemanticTokenModifiers.DefaultLibrary);
                ret.add(tokenMarker);
            }
        }

        return ret;
    }

    private static ArrayList<SemanticTokenMarker> convertCommentRanges(ArrayList<Range> comments) {
        ArrayList<SemanticTokenMarker> ret = new ArrayList<>();

        int tokenType = CommonSemanticTokens.getType("comment");

        for (Range range : comments) {
            ret.add(new SemanticTokenMarker(tokenType, range));
        }

        return ret;
    }

    private static ArrayList<SemanticTokenMarker> findComments(SchemaNode rootNode) {
        TokenSource tokenSource = rootNode.getTokenSource();
        String source = tokenSource.toString();
        ArrayList<Range> ret = new ArrayList<>();

        int index = source.indexOf("#");
        while (index >= 0) {
            Position start = CSTUtils.getPositionFromOffset(tokenSource, index);
            if (CSTUtils.getLeafNodeAtPosition(rootNode, start) != null) {
                index = source.indexOf("#", index + 1);
                continue;
            }

            index = source.indexOf("\n", index + 1);

            if (index < 0) {
                index = source.length() - 1;
            }

            Position end = CSTUtils.getPositionFromOffset(tokenSource, index);

            ret.add(new Range(start, end));

            index = source.indexOf("#", index + 1);
        }

        return convertCommentRanges(ret);
    }

    // This function assumes that both of the lists are sorted, and that no elements are overlapping
    private static ArrayList<SemanticTokenMarker> mergeSemanticTokenMarkers(ArrayList<SemanticTokenMarker> lhs, ArrayList<SemanticTokenMarker> rhs) {
        ArrayList<SemanticTokenMarker> ret = new ArrayList<>(lhs.size() + rhs.size());

        int lhsIndex = 0;
        int rhsIndex = 0;
        while (
            lhsIndex < lhs.size() &&
            rhsIndex < rhs.size()
        ) {
            Position rhsPos = rhs.get(rhsIndex).getRange().getStart();
            Position lhsPos = lhs.get(lhsIndex).getRange().getStart();

            if (CSTUtils.positionLT(lhsPos, rhsPos)) {
                ret.add(lhs.get(lhsIndex));
                lhsIndex++;
            } else {
                ret.add(rhs.get(rhsIndex));
                rhsIndex++;
            }
        }

        for (int i = lhsIndex; i < lhs.size(); i++) {
            ret.add(lhs.get(i));
        }

        for (int i = rhsIndex; i < rhs.size(); i++) {
            ret.add(rhs.get(i));
        }

        return ret;
    }

    public static SemanticTokens getSemanticTokens(EventDocumentContext context) {

        if (context.document == null) {
            return new SemanticTokens(new ArrayList<>());
        }

        SchemaNode node = context.document.getRootNode();
        if (node == null) {
            return new SemanticTokens(new ArrayList<>());
        }

        ArrayList<SemanticTokenMarker> comments = findComments(context.document.getRootNode());

        ArrayList<SemanticTokenMarker> markers = traverseCST(node, context.logger);
        List<Integer> compactMarkers = SemanticTokenMarker.concatCompactForm(
            mergeSemanticTokenMarkers(markers, comments)
        );

        return new SemanticTokens(compactMarkers);
    }
}
