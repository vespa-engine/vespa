// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import ai.vespa.intellij.schema.lexer.SdLexerAdapter;
import ai.vespa.intellij.schema.psi.SdTypes;

import java.util.HashSet;

/**
 * Defines the syntax highlighting of an SD file.
 *
 * @author Shahar Ariel
 */
public class SdSyntaxHighlighter extends SyntaxHighlighterBase {
    
    private static final HashSet<IElementType> keyWordsSet = initKeyWordsSet();
    private static final HashSet<IElementType> constantsSet = initConstantsSet();
    
    public static final TextAttributesKey IDENTIFIER =
        createTextAttributesKey("SD_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey CONSTANT =
        createTextAttributesKey("SD_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT);
    public static final TextAttributesKey KEY =
        createTextAttributesKey("SD_KEY", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey SYMBOL =
        createTextAttributesKey("SD_SYMBOL", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey STRING =
        createTextAttributesKey("SD_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey NUMBER =
        createTextAttributesKey("SD_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey COMMENT =
        createTextAttributesKey("SD_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey BAD_CHARACTER =
        createTextAttributesKey("Sd_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);
    
    
    private static final TextAttributesKey[] BAD_CHAR_KEYS = new TextAttributesKey[]{BAD_CHARACTER};
    private static final TextAttributesKey[] IDENTIFIER_KEYS = new TextAttributesKey[]{IDENTIFIER};
    private static final TextAttributesKey[] CONSTANT_KEYS = new TextAttributesKey[]{CONSTANT};
    private static final TextAttributesKey[] KEY_KEYS = new TextAttributesKey[]{KEY};
    private static final TextAttributesKey[] SYMBOL_KEYS = new TextAttributesKey[]{SYMBOL};
    private static final TextAttributesKey[] STRING_KEYS = new TextAttributesKey[]{STRING};
    private static final TextAttributesKey[] NUMBER_KEYS = new TextAttributesKey[]{NUMBER};
    private static final TextAttributesKey[] COMMENT_KEYS = new TextAttributesKey[]{COMMENT};
    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];
    
    @Override
    public Lexer getHighlightingLexer() {
        return new SdLexerAdapter();
    }
    
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType.equals(SdTypes.IDENTIFIER_VAL) || tokenType.equals(SdTypes.IDENTIFIER_WITH_DASH_VAL)) {
            return IDENTIFIER_KEYS;
        } else if (keyWordsSet.contains(tokenType)) {
            return KEY_KEYS;
        } else if (tokenType.equals(SdTypes.SYMBOL) || tokenType.equals(SdTypes.COMMA)) {
            return SYMBOL_KEYS;
        } else if (tokenType.equals(SdTypes.STRING_REG)) {
            return STRING_KEYS;
        } else if (tokenType.equals(SdTypes.INTEGER_REG) || tokenType.equals(SdTypes.FLOAT_REG)) {
            return NUMBER_KEYS;
        } else if (tokenType.equals(SdTypes.COMMENT)) {
            return COMMENT_KEYS;
        } else if (constantsSet.contains(tokenType)) {
            return CONSTANT_KEYS;
        } else if (tokenType.equals(TokenType.BAD_CHARACTER)) {
            return BAD_CHAR_KEYS;
        } else {
            return EMPTY_KEYS;
        }
    }
    
    private static HashSet<IElementType> initKeyWordsSet() {
        HashSet<IElementType> keyWords = new HashSet<>();
        keyWords.add(SdTypes.SEARCH);
        keyWords.add(SdTypes.SCHEMA);
        keyWords.add(SdTypes.FIELD);
        keyWords.add(SdTypes.TYPE);
        keyWords.add(SdTypes.INDEXING);
        keyWords.add(SdTypes.INPUT);
        keyWords.add(SdTypes.DOCUMENT_SUMMARY);
        keyWords.add(SdTypes.INHERITS);
        keyWords.add(SdTypes.IMPORT);
        keyWords.add(SdTypes.AS);
        keyWords.add(SdTypes.FIELDSET);
        keyWords.add(SdTypes.FIELDS);
        keyWords.add(SdTypes.CONSTANT);
        keyWords.add(SdTypes.FILE);
        keyWords.add(SdTypes.URI);
        keyWords.add(SdTypes.OUTPUT);
        keyWords.add(SdTypes.ONNX_MODEL);
        keyWords.add(SdTypes.ANNOTATION);
        keyWords.add(SdTypes.RANK_PROFILE);
        keyWords.add(SdTypes.SIGNIFICANCE);
        keyWords.add(SdTypes.MATCH_PHASE);
        keyWords.add(SdTypes.FIRST_PHASE);
        keyWords.add(SdTypes.EXPRESSION);
        keyWords.add(SdTypes.SECOND_PHASE);
        keyWords.add(SdTypes.RANK_PROPERTIES);
        keyWords.add(SdTypes.MACRO);
        keyWords.add(SdTypes.FUNCTION);
        keyWords.add(SdTypes.INLINE);
        keyWords.add(SdTypes.SUMMARY_FEATURES);
        keyWords.add(SdTypes.MATCH_FEATURES);
        keyWords.add(SdTypes.RANK_FEATURES);
        keyWords.add(SdTypes.CONSTANTS);
        keyWords.add(SdTypes.DOCUMENT);
        keyWords.add(SdTypes.STRUCT);
        keyWords.add(SdTypes.STRUCT_FIELD);
        keyWords.add(SdTypes.MATCH);
        keyWords.add(SdTypes.DISTANCE_METRIC);
        keyWords.add(SdTypes.ALIAS);
        keyWords.add(SdTypes.STEMMING);
        keyWords.add(SdTypes.RANK);
        keyWords.add(SdTypes.INDEXING_REWRITE);
        keyWords.add(SdTypes.QUERY_COMMAND);
        keyWords.add(SdTypes.BOLDING);
        keyWords.add(SdTypes.HNSW);
        keyWords.add(SdTypes.SORTING);
        keyWords.add(SdTypes.RANK_TYPE);
        keyWords.add(SdTypes.WEIGHTEDSET);
        keyWords.add(SdTypes.DICTIONARY);
        keyWords.add(SdTypes.ID);
        keyWords.add(SdTypes.NORMALIZING);
        keyWords.add(SdTypes.WEIGHT);
    
        return keyWords;
    }
    
    private static HashSet<IElementType> initConstantsSet() {
        HashSet<IElementType> constants = new HashSet<>();
        constants.add(SdTypes.RAW_AS_BASE64_IN_SUMMARY);
        constants.add(SdTypes.OMIT_SUMMARY_FEATURES);
        constants.add(SdTypes.FROM_DISK);
        constants.add(SdTypes.IGNORE_DEFAULT_RANK_FEATURES);
        constants.add(SdTypes.ATTRIBUTE);
        constants.add(SdTypes.ORDER);
        constants.add(SdTypes.MAX_HITS);
        constants.add(SdTypes.DIVERSITY);
        constants.add(SdTypes.MIN_GROUPS);
        constants.add(SdTypes.CUTOFF_FACTOR);
        constants.add(SdTypes.CUTOFF_STRATEGY);
        constants.add(SdTypes.NUM_THREADS_PER_SEARCH);
        constants.add(SdTypes.TERMWISE_LIMIT);
        constants.add(SdTypes.MIN_HITS_PER_THREAD);
        constants.add(SdTypes.NUM_SEARCH_PARTITIONS);
        constants.add(SdTypes.KEEP_RANK_COUNT);
        constants.add(SdTypes.RANK_SCORE_DROP_LIMIT);
        constants.add(SdTypes.RERANK_COUNT);
        constants.add(SdTypes.TOTAL_RERANK_COUNT);
        constants.add(SdTypes.TEXT);
        constants.add(SdTypes.EXACT);
        constants.add(SdTypes.EXACT_TERMINATOR);
        constants.add(SdTypes.WORD);
        constants.add(SdTypes.PREFIX);
        constants.add(SdTypes.CASED);
        constants.add(SdTypes.UNCASED);
        constants.add(SdTypes.SUBSTRING);
        constants.add(SdTypes.SUFFIX);
        constants.add(SdTypes.MAX_LENGTH);
        constants.add(SdTypes.GRAM);
        constants.add(SdTypes.GRAM_SIZE);
        constants.add(SdTypes.SUMMARY);
        constants.add(SdTypes.INDEX);
        constants.add(SdTypes.SET_LANGUAGE);
        constants.add(SdTypes.LOWERCASE);
        constants.add(SdTypes.FAST_SEARCH);
        constants.add(SdTypes.FAST_RANK);
        constants.add(SdTypes.FAST_ACCESS);
        constants.add(SdTypes.PAGED);
        constants.add(SdTypes.MUTABLE);
        constants.add(SdTypes.FILTER);
        constants.add(SdTypes.NORMAL);
        constants.add(SdTypes.NONE);
        constants.add(SdTypes.FULL);
        constants.add(SdTypes.DYNAMIC);
        constants.add(SdTypes.SOURCE);
        constants.add(SdTypes.TO);
        constants.add(SdTypes.MATCHED_ELEMENTS_ONLY);
        constants.add(SdTypes.ON);
        constants.add(SdTypes.OFF);
        constants.add(SdTypes.TRUE);
        constants.add(SdTypes.FALSE);
        constants.add(SdTypes.ARITY);
        constants.add(SdTypes.LOWER_BOUND);
        constants.add(SdTypes.UPPER_BOUND);
        constants.add(SdTypes.DENSE_POSTING_LIST_THRESHOLD);
        constants.add(SdTypes.ENABLE_BM25);
        constants.add(SdTypes.MAX_LINKS_PER_NODE);
        constants.add(SdTypes.NEIGHBORS_TO_EXPLORE_AT_INSERT);
        constants.add(SdTypes.MULTI_THREADED_INDEXING);
        constants.add(SdTypes.ASCENDING);
        constants.add(SdTypes.DESCENDING);
        constants.add(SdTypes.STRENGTH);
        constants.add(SdTypes.LOCALE);
        constants.add(SdTypes.CREATE_IF_NONEXISTENT);
        constants.add(SdTypes.REMOVE_IF_ZERO);
        return constants;
    }

}

