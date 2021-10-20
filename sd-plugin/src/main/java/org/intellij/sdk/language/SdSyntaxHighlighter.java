// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.intellij.sdk.language.psi.SdTypes;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

public class SdSyntaxHighlighter extends SyntaxHighlighterBase {
    
    private static final HashSet<IElementType> keyWordsSet = initKeyWordsSet();
    private static final HashSet<IElementType> constantsSet = initConstantsSet();
//    private static final HashSet<IElementType> symbols = initSymbolsSet();
    
    
    public static final TextAttributesKey IDENTIFIER =
        createTextAttributesKey("SD_IDENTIFIER", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey CONSTANT =
        createTextAttributesKey("SD_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT);
    public static final TextAttributesKey KEY =
        createTextAttributesKey("SD_KEY", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey SYMBOL =
        createTextAttributesKey("SD_SYMBOL", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey STRING =
        createTextAttributesKey("SD_STRING", DefaultLanguageHighlighterColors.STRING);
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
    private static final TextAttributesKey[] COMMENT_KEYS = new TextAttributesKey[]{COMMENT};
    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];
    
    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new SdLexerAdapter();
    }
    
    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType.equals(SdTypes.IDENTIFIER_VAL)) {
            return IDENTIFIER_KEYS;
//        } else if (tokenType.equals(SdTypes.KEY)) {
//            return KEY_KEYS;
//        } else if (tokenType.equals(SdTypes.VALUE)) {
//            return VALUE_KEYS;
        } else if (keyWordsSet.contains(tokenType)) {
            return KEY_KEYS;
        } else if (tokenType.equals(SdTypes.SYMBOL)) {
            return SYMBOL_KEYS;
        } else if (tokenType.equals(SdTypes.STRING)) {
            return STRING_KEYS;
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
    
//    private static HashSet<IElementType> initSymbolsSet() {
//        HashSet<IElementType> symbols = new HashSet<>();
//        symbols.add('{');
//        return symbols;
//    }
    
    private static HashSet<IElementType> initKeyWordsSet() {
        HashSet<IElementType> keyWords = new HashSet<>();
        keyWords.add(SdTypes.MACRO);
        keyWords.add(SdTypes.FIELD);
        keyWords.add(SdTypes.TYPE);
        keyWords.add(SdTypes.SEARCH);
        keyWords.add(SdTypes.DOCUMENT);
        keyWords.add(SdTypes.INHERITS);
        keyWords.add(SdTypes.STRUCT);
        keyWords.add(SdTypes.STRUCT_FIELD);
        keyWords.add(SdTypes.MATCH);
        keyWords.add(SdTypes.INDEXING);
        keyWords.add(SdTypes.RANK);
        keyWords.add(SdTypes.INDEXING_REWRITE);
        keyWords.add(SdTypes.QUERY_COMMAND);
        return keyWords;
    }
    
    private static HashSet<IElementType> initConstantsSet() {
        HashSet<IElementType> constants = new HashSet<>();
        constants.add(SdTypes.SUMMARY);
        constants.add(SdTypes.ATTRIBUTE);
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
        constants.add(SdTypes.INDEX);
        constants.add(SdTypes.FAST_SEARCH);
        constants.add(SdTypes.FAST_ACCESS);
        constants.add(SdTypes.ALIAS);
        constants.add(SdTypes.SORTING);
        constants.add(SdTypes.DISTANCE_METRIC);
        constants.add(SdTypes.FILTER);
        constants.add(SdTypes.NORMAL);
        constants.add(SdTypes.NONE);
        constants.add(SdTypes.FULL);
        constants.add(SdTypes.DYNAMIC);
        constants.add(SdTypes.MATCHED_ELEMENTS_ONLY);
        
        return constants;
    }
}

