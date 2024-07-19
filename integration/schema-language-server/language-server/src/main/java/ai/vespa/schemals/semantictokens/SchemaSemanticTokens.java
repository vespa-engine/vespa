package ai.vespa.schemals.semantictokens;

import java.io.PrintStream;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensServerFull;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;

import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.Visitor;
import ai.vespa.schemals.context.EventContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.TokenSource;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.dataType;

public class SchemaSemanticTokens implements Visitor {

    private static final ArrayList<String> manuallyRegisteredLSPNames = new ArrayList<String>() {{
        add("type");
        add("comment");
    }};

    // Keyword
    private static final List<SymbolType> userDefinedSymbolTypes = new ArrayList<SymbolType>() {{
        add(SymbolType.SCHEMA);
        add(SymbolType.DOCUMENT);
        add(SymbolType.FIELD);
        add(SymbolType.STRUCT);
        add(SymbolType.ANNOTATION);
        add(SymbolType.RANK_PROFILE);
        add(SymbolType.FIELDSET);
        add(SymbolType.STRUCT_FIELD);
        add(SymbolType.FUNCTION);
        add(SymbolType.TYPE_UNKNOWN);
        add(SymbolType.SUBFIELD);
        add(SymbolType.PARAMETER);
    }};

    private static final ArrayList<TokenType> keywordTokens = new ArrayList<TokenType>() {{
        add(TokenType.ANNOTATION);
        add(TokenType.DOCUMENT);
        add(TokenType.FIELD);
        add(TokenType.FIELDSET); 
        add(TokenType.FIRST_PHASE);
        add(TokenType.INHERITS);
        add(TokenType.RANK_PROFILE);
        add(TokenType.SCHEMA);
        add(TokenType.SEARCH);
        add(TokenType.STRUCT);
        add(TokenType.STRUCT_FIELD);
        add(TokenType.TYPE);
        add(TokenType.FUNCTION);
        add(TokenType.RANK_PROPERTIES);
        add(TokenType.MATCHFEATURES_SL);
        add(TokenType.IMPORT);
        add(TokenType.INPUTS);
        add(TokenType.DOCUMENT_SUMMARY);
        add(TokenType.AS);
        add(TokenType.SUMMARY);
        add(TokenType.INDEXING);
    }};

    // Other
    private static final Map<TokenType, String> schemaTokenTypeLSPNameMap = new HashMap<TokenType, String>() {{
        put(TokenType.DOUBLE, "number");
        put(TokenType.INTEGER, "number");
        put(TokenType.LONG, "number");
        put(TokenType.DOUBLEQUOTEDSTRING, "string");
        put(TokenType.SINGLEQUOTEDSTRING, "string");
    }};


    // ========= Ranking expressions =========
    // Keyword
    private static final ArrayList<ai.vespa.schemals.parser.rankingexpression.Token.TokenType> rankingExpressionKeywordTokens = new ArrayList<>() {{
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.IF);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.IN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.F);

        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.TRUE);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.FALSE);

        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.AVG);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.COUNT);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.MAX);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.MEDIAN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.MIN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.PROD);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.SUM);
    }};

    // Operations
    private static final ArrayList<ai.vespa.schemals.parser.rankingexpression.Token.TokenType> rankingExpressionOperationTokens = new ArrayList<>() {{
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ADD);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.SUB);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.DIV);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.MUL);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.DOT);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.MOD);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.POWOP);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.GREATEREQUAL);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.GREATER);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.LESSEQUAL);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.LESS);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.APPROX);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.NOTEQUAL);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.EQUAL);
    }};

    // Functions
    private static final ArrayList<ai.vespa.schemals.parser.rankingexpression.Token.TokenType> rankingExpressioFunctionTokens = new ArrayList<>() {{
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ABS);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ACOS);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ASIN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ATAN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.CEIL);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.COS);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.COSH);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ELU);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.EXP);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.FABS);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.FLOOR);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ISNAN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.LOG);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.LOG10);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.RELU);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ROUND);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.SIGMOID);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.SIGN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.SIN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.SINH);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.SQUARE);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.SQRT);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.TAN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.TANH);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ERF);

        // Space in the ccc file as well :)
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ATAN2);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.FMOD);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.LDEXP);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.POW);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.BIT);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.HAMMING);

        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.MAP);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.MAP_SUBSPACES);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.UNPACK_BITS);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.REDUCE);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.JOIN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.MERGE);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.RENAME);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.CONCAT);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.TENSOR);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.RANGE);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.DIAG);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.RANDOM);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.L1_NORMALIZE);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.L2_NORMALIZE);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.EUCLIDEAN_DISTANCE);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.COSINE_SIMILARITY);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.MATMUL);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.SOFTMAX);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.XW_PLUS_B);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ARGMAX);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ARGMIN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.CELL_CAST);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.EXPAND);
    }};

    // Other
    private static final Map<ai.vespa.schemals.parser.rankingexpression.Token.TokenType, String> rankingExpressionTokenTypeLSPNameMap = new HashMap<ai.vespa.schemals.parser.rankingexpression.Token.TokenType, String>() {{
        put(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ISNAN, "function");
        put(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.STRING, "string");
        put(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.INTEGER, "number");
        put(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.FLOAT, "number");
    }};


    private static final Set<ai.vespa.schemals.parser.indexinglanguage.Token.TokenType> indexingLanguageOperators = new HashSet<>() {{
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.BASE64_DECODE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.BASE64_ENCODE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.ECHO);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.FLATTEN);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.GET_FIELD);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.GET_VAR);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.HEX_DECODE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.HEX_ENCODE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.HOST_NAME);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.INPUT);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.JOIN);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.LOWER_CASE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.NGRAM);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.NORMALIZE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.NOW);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.SET_LANGUAGE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.SET_VAR);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.SUBSTRING);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.SPLIT);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TOKENIZE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TRIM);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TO_INT);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TO_POS);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TO_BOOL);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TO_BYTE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TO_LONG);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TO_WSET);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TO_ARRAY);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TO_DOUBLE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TO_FLOAT);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TO_STRING);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.EMBED);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.HASH);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.TO_EPOCH_SECOND);
    }};

    private static final Set<ai.vespa.schemals.parser.indexinglanguage.Token.TokenType> indexingLanguageOutputs = new HashSet<>() {{
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.ATTRIBUTE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.INDEX);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.SUMMARY);
    }};

    private static final Set<ai.vespa.schemals.parser.indexinglanguage.Token.TokenType> indexingLanguageKeywords = new HashSet<>() {{
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.IF);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.FOR_EACH);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.SWITCH);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.SELECT_INPUT);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.ELSE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.CASE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.CASE_DEFAULT);
    }};

    private static Map<SymbolType, Integer> identifierTypeMap;
    private static final HashMap<SymbolType, String> identifierTypeLSPNameMap = new HashMap<SymbolType, String>() {{
        put(SymbolType.SCHEMA, "namespace");
        put(SymbolType.DOCUMENT, "class");
        put(SymbolType.FIELD, "variable");
        put(SymbolType.FIELDSET, "variable");
        put(SymbolType.STRUCT, "variable");
        put(SymbolType.STRUCT_FIELD, "variable");
        put(SymbolType.RANK_PROFILE, "variable");
        put(SymbolType.FUNCTION, "function");
        put(SymbolType.DOCUMENT_SUMMARY, "variable");
        put(SymbolType.PARAMETER, "parameter");
    }};

    private static ArrayList<String> tokenTypes;
    private static Map<TokenType, Integer> schemaTokenTypeMap;
    private static Map<ai.vespa.schemals.parser.rankingexpression.Token.TokenType, Integer> rankExpressionTokenTypeMap;

    private static int addTokenType(String name) {
        int index = tokenTypes.indexOf(name);
        if (index == -1) {
            index = tokenTypes.size();
            tokenTypes.add(name);
        }
        return index;
    }

    static {
        tokenTypes = new ArrayList<String>();

        // Manually added semantic tokens
        tokenTypes.addAll(manuallyRegisteredLSPNames);
        int keywordIndex = addTokenType("keyword");

        // Add symbol semantic tokens
        identifierTypeMap = new HashMap<SymbolType, Integer>();

        for (Map.Entry<SymbolType, String> set : identifierTypeLSPNameMap.entrySet()) {
            int index = addTokenType(set.getValue());
            identifierTypeMap.put(set.getKey(), index);
        }

        // Create Map for Schema Tokens
        schemaTokenTypeMap = new HashMap<TokenType, Integer>();

        for (var set : schemaTokenTypeLSPNameMap.entrySet()) {
            int index = addTokenType(set.getValue());
            schemaTokenTypeMap.put(set.getKey(), index);
        }
        
        for (TokenType type : keywordTokens) {
            schemaTokenTypeMap.put(type, keywordIndex);
        }

        // Create Map for RankExpression Tokens
        rankExpressionTokenTypeMap = new HashMap<ai.vespa.schemals.parser.rankingexpression.Token.TokenType, Integer>();

        for (var set : rankingExpressionTokenTypeLSPNameMap.entrySet()) {
            int index = addTokenType(set.getValue());
            rankExpressionTokenTypeMap.put(set.getKey(), index);
        }

        for (var type : rankingExpressionKeywordTokens) {
            rankExpressionTokenTypeMap.put(type, keywordIndex);
        }

        int operationIndex = addTokenType("operation");
        for (var type : rankingExpressionOperationTokens) {
            rankExpressionTokenTypeMap.put(type, operationIndex);
        }

        int functionIndex = addTokenType("function");
        for (var type : rankingExpressioFunctionTokens) {
            rankExpressionTokenTypeMap.put(type, functionIndex);
        }

    }


    private static final ArrayList<String> tokenModifiers = new ArrayList<String>();

    public static SemanticTokensWithRegistrationOptions getSemanticTokensRegistrationOptions() {
        return new SemanticTokensWithRegistrationOptions(
            new SemanticTokensLegend(tokenTypes, tokenModifiers),
            new SemanticTokensServerFull(false)
        );
    }

    private static class SemanticTokenMarker {
        private static final int LINE_INDEX = 0;
        private static final int COLUMN_INDEX = 1;

        private int tokenType;
        private Range range;

        SemanticTokenMarker(int tokenType, SchemaNode node) {
            this(tokenType, node.getRange());
        }
        
        SemanticTokenMarker(int tokenType, Range range) {
            this.tokenType = tokenType;
            this.range = range;
        }

        Range getRange() { return range; }

        private ArrayList<Integer> compactForm() {
            int length = range.getEnd().getCharacter() - range.getStart().getCharacter();

            return new ArrayList<Integer>() {{
                add(range.getStart().getLine());
                add(range.getStart().getCharacter());
                add(length);
                add(tokenType);
                add(0);
            }};
        }

        static ArrayList<Integer> concatCompactForm(ArrayList<SemanticTokenMarker> markers) {
            ArrayList<Integer> ret = new ArrayList<>(markers.size() * 5);

            if (markers.size() == 0) {
                return ret;
            }

            ret.addAll(markers.get(0).compactForm());

            for (int i = 1; i < markers.size(); i++) {
                ArrayList<Integer> markerCompact = markers.get(i).compactForm();
                ArrayList<Integer> lastMarkerCompact = markers.get(i - 1).compactForm();
                markerCompact.set(LINE_INDEX, markerCompact.get(LINE_INDEX) - lastMarkerCompact.get(LINE_INDEX));
                if (markerCompact.get(LINE_INDEX) == 0) {
                    markerCompact.set(COLUMN_INDEX, markerCompact.get(COLUMN_INDEX) - lastMarkerCompact.get(COLUMN_INDEX));
                }
                ret.addAll(markerCompact);
            }

            return ret;
        }
    }

    private static ArrayList<SemanticTokenMarker> traverseCST(SchemaNode node, PrintStream logger) {
        ArrayList<SemanticTokenMarker> ret = new ArrayList<SemanticTokenMarker>();

        TokenType schemaType = node.getSchemaType();
        var rankExpressionType = node.getRankExpressionType();
        var indexinglanguageType = node.getIndexingLanguageType();

        // TODO: this became a bit ugly with the map stuff
        if (node.isASTInstance(dataType.class) && (!node.hasSymbol() || node.getSymbol().getType() == SymbolType.MAP_KEY || node.getSymbol().getType() == SymbolType.MAP_VALUE)) {

            Integer tokenType = tokenTypes.indexOf("type");
            if (tokenType != -1) {
                // this will leave <> uncolored, as we are only interested in marking the actual token
                Range markerRange = CSTUtils.findFirstLeafChild(node).getRange();
                ret.add(new SemanticTokenMarker(tokenType, markerRange));
            }

        } else if (
            node.hasSymbol() && 
            (
                node.getSymbol().getStatus() == SymbolStatus.REFERENCE ||
                node.getSymbol().getStatus() == SymbolStatus.DEFINITION
            ) && 
            userDefinedSymbolTypes.contains(node.getSymbol().getType())
        ) {
            Integer tokenType = identifierTypeMap.get(node.getSymbol().getType());
            
            if (tokenType != null) {
                ret.add(new SemanticTokenMarker(tokenType, node));
            }

        } else if (node.hasSymbol() && node.getSymbol().getStatus() == SymbolStatus.BUILTIN_REFERENCE) {
            Integer tokenType = identifierTypeMap.get(node.getSymbol().getType());

            if (tokenType != null) {
                ret.add(new SemanticTokenMarker(tokenType, node));
            }

        } else if (schemaType != null) {

            Integer tokenType = schemaTokenTypeMap.get(schemaType);
            if (tokenType != null) {
                ret.add(new SemanticTokenMarker(tokenType, node));
            }

        } else if (rankExpressionType != null) {

            Integer tokenType = rankExpressionTokenTypeMap.get(rankExpressionType);
            if (tokenType != null) {
                ret.add(new SemanticTokenMarker(tokenType, node));
            }

        } else if (indexinglanguageType != null) {
            if (indexingLanguageOutputs.contains(indexinglanguageType)) {
                Integer tokenType = tokenTypes.indexOf("type");
                Range markerRange = CSTUtils.findFirstLeafChild(node).getRange();
                ret.add(new SemanticTokenMarker(tokenType, markerRange));
            } else if (indexingLanguageKeywords.contains(indexinglanguageType)) {
                Integer tokenType = tokenTypes.indexOf("keyword");
                Range markerRange = CSTUtils.findFirstLeafChild(node).getRange();
                ret.add(new SemanticTokenMarker(tokenType, markerRange));
            } else if (indexingLanguageOperators.contains(indexinglanguageType)) {
                Integer tokenType = tokenTypes.indexOf("function");
                Range markerRange = CSTUtils.findFirstLeafChild(node).getRange();
                ret.add(new SemanticTokenMarker(tokenType, markerRange));
            } else if (indexinglanguageType == ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.STRING) {
                Integer tokenType = tokenTypes.indexOf("string");
                Range markerRange = CSTUtils.findFirstLeafChild(node).getRange();
                ret.add(new SemanticTokenMarker(tokenType, markerRange));
            }
        } else {
            
            for (SchemaNode child : node) {
                ArrayList<SemanticTokenMarker> markers = traverseCST(child, logger);
                ret.addAll(markers);
            }
        }


        return ret;
    }

    private static ArrayList<SemanticTokenMarker> convertCommentRanges(ArrayList<Range> comments) {
        ArrayList<SemanticTokenMarker> ret = new ArrayList<>();

        int tokenType = tokenTypes.indexOf("comment");

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

    public static SemanticTokens getSemanticTokens(EventContext context) {

        if (context.document == null) {
            return new SemanticTokens(new ArrayList<>());
        }

        SchemaNode node = context.document.getRootNode();
        if (node == null) {
            return new SemanticTokens(new ArrayList<>());
        }

        ArrayList<SemanticTokenMarker> comments = findComments(context.document.getRootNode());

        ArrayList<SemanticTokenMarker> markers = traverseCST(node, context.logger);
        ArrayList<Integer> compactMarkers = SemanticTokenMarker.concatCompactForm(
            mergeSemanticTokenMarkers(markers, comments)
        );

        return new SemanticTokens(compactMarkers);
    }
}
