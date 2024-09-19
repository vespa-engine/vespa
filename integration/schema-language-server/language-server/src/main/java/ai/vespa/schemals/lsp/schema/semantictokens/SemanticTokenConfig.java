package ai.vespa.schemals.lsp.schema.semantictokens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;

import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Token.TokenType;

class SemanticTokenConfig {
    static final ArrayList<String> manuallyRegisteredLSPNames = new ArrayList<String>() {{
        add(SemanticTokenTypes.Type);
        add(SemanticTokenTypes.Comment);
        add(SemanticTokenTypes.Macro);
        add(SemanticTokenTypes.Enum);
        add(SemanticTokenTypes.EnumMember);
        add(SemanticTokenTypes.Property);
    }};

    static final List<String> tokenModifiers = new ArrayList<>() {{
        add(SemanticTokenModifiers.Definition);
        add(SemanticTokenModifiers.Readonly);
        add(SemanticTokenModifiers.DefaultLibrary);
    }};

    static final List<SymbolType> userDefinedSymbolTypes = new ArrayList<SymbolType>() {{
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
        add(SymbolType.LABEL);
        add(SymbolType.QUERY_INPUT);
        add(SymbolType.RANK_CONSTANT);
        add(SymbolType.PROPERTY);
        add(SymbolType.LAMBDA_FUNCTION);
        add(SymbolType.DIMENSION);
    }};

    // Keyword
    static final ArrayList<TokenType> keywordTokens = new ArrayList<TokenType>() {{
        add(TokenType.ALIAS);
        add(TokenType.ANNOTATION);
        add(TokenType.APPROXIMATE_THRESHOLD);
        add(TokenType.AS);
        add(TokenType.ATTRIBUTE);
        add(TokenType.BOLDING);
        add(TokenType.CONSTANT);
        add(TokenType.CONSTANTS);
        add(TokenType.DIVERSITY);
        add(TokenType.DOCUMENT);
        add(TokenType.DOCUMENT_SUMMARY);
        add(TokenType.EXPRESSION_SL);
        add(TokenType.FIELD);
        add(TokenType.FIELDS);
        add(TokenType.FIELDSET); 
        add(TokenType.FIRST_PHASE);
        add(TokenType.FUNCTION);
        add(TokenType.GLOBAL_PHASE);
        add(TokenType.ID);
        add(TokenType.IGNORE_DEFAULT_RANK_FEATURES);
        add(TokenType.IMPORT);
        add(TokenType.INDEX);
        add(TokenType.INDEXING);
        add(TokenType.INHERITS);
        add(TokenType.INPUTS);
        add(TokenType.MACRO);
        add(TokenType.MATCH);
        add(TokenType.MATCHFEATURES_SL);
        add(TokenType.MATCH_PHASE);
        add(TokenType.MUTATE);
        add(TokenType.NORMALIZING);
        add(TokenType.NUM_SEARCH_PARTITIONS);
        add(TokenType.NUM_THREADS_PER_SEARCH);
        add(TokenType.ONNX_MODEL);
        add(TokenType.POST_FILTER_THRESHOLD);
        add(TokenType.QUERY_COMMAND);
        add(TokenType.RANK);
        add(TokenType.RANKFEATURES_SL);
        add(TokenType.RANK_PROFILE);
        add(TokenType.RANK_PROPERTIES);
        add(TokenType.RANK_TYPE);
        add(TokenType.RAW_AS_BASE64_IN_SUMMARY);
        add(TokenType.SCHEMA);
        add(TokenType.SEARCH);
        add(TokenType.SECOND_PHASE);
        add(TokenType.SORTING);
        add(TokenType.STEMMING);
        add(TokenType.STRICT);
        add(TokenType.STRUCT);
        add(TokenType.STRUCT_FIELD);
        add(TokenType.SUMMARY);
        add(TokenType.SUMMARYFEATURES_SL);
        add(TokenType.SUMMARY_TO);
        add(TokenType.TARGET_HITS_MAX_ADJUSTMENT_FACTOR);
        add(TokenType.TERMWISE_LIMIT);
        add(TokenType.TYPE);
        add(TokenType.WEIGHT);
        add(TokenType.WEIGHTEDSET);
        add(TokenType.HNSW);
        add(TokenType.MAX_LINKS_PER_NODE);
        add(TokenType.NEIGHBORS_TO_EXPLORE_AT_INSERT);
        add(TokenType.UPPER_BOUND);
        add(TokenType.LOWER_BOUND);
        add(TokenType.ARITY);
        add(TokenType.ENABLE_BM25);
        add(TokenType.DENSE_POSTING_LIST_THRESHOLD);

        // maybe some of these should be something other than keyword
        add(TokenType.DISTANCE_METRIC);
        add(TokenType.FAST_ACCESS);
        add(TokenType.FAST_SEARCH);
        add(TokenType.FAST_RANK);
        add(TokenType.PAGED);
        add(TokenType.MUTABLE);
        add(TokenType.LOCALE);
        add(TokenType.STRENGTH);
        add(TokenType.ASCENDING);
        add(TokenType.DESCENDING);
        add(TokenType.DICTIONARY); // TODO: color dictionary items

    }};

    // Other
    static final Map<TokenType, String> schemaTokenTypeLSPNameMap = new HashMap<TokenType, String>() {{
        put(TokenType.DOUBLE, "number");
        put(TokenType.INTEGER, "number");
        put(TokenType.LONG, "number");
        put(TokenType.DOUBLEQUOTEDSTRING, "string");
        put(TokenType.SINGLEQUOTEDSTRING, "string");
        put(TokenType.QUERY, "function");

        // TODO: figure out boolean color
        put(TokenType.ON, SemanticTokenTypes.Type);
        put(TokenType.OFF, SemanticTokenTypes.Type);
        put(TokenType.TRUE, SemanticTokenTypes.Type);
        put(TokenType.FALSE, SemanticTokenTypes.Type);

    }};


    // ========= Ranking expressions =========
    // Keyword
    static final ArrayList<ai.vespa.schemals.parser.rankingexpression.Token.TokenType> rankingExpressionKeywordTokens = new ArrayList<>() {{
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.IF);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.IN);

        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.TRUE);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.FALSE);

        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.AVG);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.COUNT);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.MAX);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.MEDIAN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.MIN);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.PROD);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.SUM);

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

    // Operations
    static final ArrayList<ai.vespa.schemals.parser.rankingexpression.Token.TokenType> rankingExpressionOperationTokens = new ArrayList<>() {{
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
    static final ArrayList<ai.vespa.schemals.parser.rankingexpression.Token.TokenType> rankingExpressioFunctionTokens = new ArrayList<>() {{
        // Space in the ccc file as well :)
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ATAN2);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.FMOD);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.LDEXP);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.POW);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.BIT);
        add(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.HAMMING);
    }};

    // Other
    static final Map<ai.vespa.schemals.parser.rankingexpression.Token.TokenType, String> rankingExpressionTokenTypeLSPNameMap = new HashMap<ai.vespa.schemals.parser.rankingexpression.Token.TokenType, String>() {{
        put(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.ISNAN, "function");
        put(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.STRING, "string");
        put(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.INTEGER, "number");
        put(ai.vespa.schemals.parser.rankingexpression.Token.TokenType.FLOAT, "number");
    }};


    static final Set<ai.vespa.schemals.parser.indexinglanguage.Token.TokenType> indexingLanguageOperators = new HashSet<>() {{
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

    static final Set<ai.vespa.schemals.parser.indexinglanguage.Token.TokenType> indexingLanguageOutputs = new HashSet<>() {{
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.ATTRIBUTE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.INDEX);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.SUMMARY);
    }};

    static final Set<ai.vespa.schemals.parser.indexinglanguage.Token.TokenType> indexingLanguageKeywords = new HashSet<>() {{
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.IF);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.FOR_EACH);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.SWITCH);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.SELECT_INPUT);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.ELSE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.CASE);
        add(ai.vespa.schemals.parser.indexinglanguage.Token.TokenType.CASE_DEFAULT);
    }};

    static final HashMap<SymbolType, String> identifierTypeLSPNameMap = new HashMap<SymbolType, String>() {{
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
        put(SymbolType.LABEL, "variable");
        put(SymbolType.QUERY_INPUT, "variable");
        put(SymbolType.RANK_CONSTANT, "variable");
        put(SymbolType.PROPERTY, "property");
        put(SymbolType.LAMBDA_FUNCTION, "keyword");
        put(SymbolType.DIMENSION, "keyword");
        put(SymbolType.ANNOTATION, "variable");
    }};
}
