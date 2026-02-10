// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.language.Language;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the text() function and shared behavior between text() and userInput().
 * Parameterized tests verify that grammar overrides and allowEmpty work identically
 * for both functions via the shared buildTextInput helper.
 */
public class TextInputTestCase {

    private YqlParser parser;

    @BeforeEach
    public void setUp() {
        parser = new YqlParser(new ParserEnvironment());
    }

    // --- Shared behavior: parameterized over text() and userInput() ---

    static Stream<Arguments> textFunctions() {
        return Stream.of(
                //          label,        field,     yqlTemplate (use %s for annotation prefix and %s for input)
                Arguments.of("text",      "title",   "select foo from bar where title contains (%s text(\"%s\"))"),
                Arguments.of("userInput", "default", "select foo from bar where %s userInput(\"%s\")")
        );
    }

    @ParameterizedTest(name = "{0}: grammar:raw produces ExactStringItem")
    @MethodSource("textFunctions")
    void grammarRawProducesExactStringItem(String label, String field, String yqlTemplate) {
        String yql = String.format(yqlTemplate, "{grammar:\"raw\"}", "a b");
        Item root = parse(yql).getRoot();
        assertInstanceOf(ExactStringItem.class, root);
        WordItem word = (WordItem) root;
        assertEquals(field, word.getIndexName());
        assertEquals("a b", word.getWord());
    }

    @ParameterizedTest(name = "{0}: grammar:all produces AndItem")
    @MethodSource("textFunctions")
    void grammarAllProducesAnd(String label, String field, String yqlTemplate) {
        Item root = parse(String.format(yqlTemplate, "{grammar:\"all\"}", "a b")).getRoot();
        assertCompositeOfWords(root, AndItem.class, field, 2);
    }

    @ParameterizedTest(name = "{0}: grammar:any produces OrItem")
    @MethodSource("textFunctions")
    void grammarAnyProducesOr(String label, String field, String yqlTemplate) {
        Item root = parse(String.format(yqlTemplate, "{grammar:\"any\"}", "a b")).getRoot();
        assertCompositeOfWords(root, OrItem.class, field, 2);
    }

    @ParameterizedTest(name = "{0}: grammar:weakAnd produces WeakAndItem")
    @MethodSource("textFunctions")
    void grammarWeakAndProducesWeakAnd(String label, String field, String yqlTemplate) {
        Item root = parse(String.format(yqlTemplate, "{grammar:\"weakAnd\"}", "a b")).getRoot();
        assertCompositeOfWords(root, WeakAndItem.class, field, 2);
    }

    static Stream<Arguments> textFunctionsForAllowEmpty() {
        return Stream.of(
                Arguments.of("text",      "select foo from bar where title contains ([{allowEmpty:true}] text(@q))"),
                Arguments.of("userInput", "select foo from bar where [{allowEmpty:true}] userInput(@q)")
        );
    }

    @ParameterizedTest(name = "{0}: allowEmpty with empty input returns NullItem")
    @MethodSource("textFunctionsForAllowEmpty")
    void allowEmptyReturnsNullItem(String label, String yql) {
        Item root = parse(yql, "q", "").getRoot();
        assertInstanceOf(NullItem.class, root);
    }

    @ParameterizedTest(name = "{0}: targetHits sets N on WeakAndItem")
    @MethodSource("textFunctions")
    void targetHitsSetsWeakAndN(String label, String field, String yqlTemplate) {
        String yql = String.format(yqlTemplate, "{targetHits:50}", "a b");
        Item root = parse(yql).getRoot();
        assertInstanceOf(WeakAndItem.class, root);
        WeakAndItem weakAnd = (WeakAndItem) root;
        assertEquals(50, weakAnd.getN());
        assertEquals(2, weakAnd.getItemCount());
    }

    static Stream<Arguments> textFunctionsForLanguage() {
        return Stream.of(
                Arguments.of("text",      "select foo from bar where title contains ({language:\"ja\"} text(\"\u30ab\u30bf\u30ab\u30ca\"))"),
                Arguments.of("userInput", "select foo from bar where {language:\"ja\"} userInput(\"\u30ab\u30bf\u30ab\u30ca\")")
        );
    }

    @ParameterizedTest(name = "{0}: language annotation sets language on result")
    @MethodSource("textFunctionsForLanguage")
    void languageAnnotationSetsLanguage(String label, String yql) {
        Item root = parse(yql).getRoot();
        assertEquals(Language.JAPANESE, root.getLanguage());
    }

    @ParameterizedTest(name = "{0}: grammar.composite:and produces AndItem")
    @MethodSource("textFunctions")
    void grammarCompositeAndProducesAnd(String label, String field, String yqlTemplate) {
        Item root = parse(String.format(yqlTemplate, "{grammar.composite:\"and\"}", "a b")).getRoot();
        assertCompositeOfWords(root, AndItem.class, field, 2);
    }

    @ParameterizedTest(name = "{0}: grammar.composite:or produces OrItem")
    @MethodSource("textFunctions")
    void grammarCompositeOrProducesOr(String label, String field, String yqlTemplate) {
        Item root = parse(String.format(yqlTemplate, "{grammar.composite:\"or\"}", "a b")).getRoot();
        assertCompositeOfWords(root, OrItem.class, field, 2);
    }

    @ParameterizedTest(name = "{0}: stem:false disables stemming on children")
    @MethodSource("textFunctions")
    void stemFalseDisablesStemming(String label, String field, String yqlTemplate) {
        Item root = parse(String.format(yqlTemplate, "{stem:false}", "a")).getRoot();
        WordItem word = getFirstWord(root);
        assertTrue(word.isStemmed(), "stem:false should mark word as pre-stemmed (isStemmed=true)");
    }

    @ParameterizedTest(name = "{0}: ranked:false sets ranked=false on children")
    @MethodSource("textFunctions")
    void rankedFalseSetsUnranked(String label, String field, String yqlTemplate) {
        Item root = parse(String.format(yqlTemplate, "{ranked:false}", "a")).getRoot();
        WordItem word = getFirstWord(root);
        assertFalse(word.isRanked(), "ranked:false should set isRanked=false");
    }

    @ParameterizedTest(name = "{0}: filter:true sets filter=true on children")
    @MethodSource("textFunctions")
    void filterTrueSetsFilter(String label, String field, String yqlTemplate) {
        Item root = parse(String.format(yqlTemplate, "{filter:true}", "a")).getRoot();
        WordItem word = getFirstWord(root);
        assertTrue(word.isFilter(), "filter:true should set isFilter=true");
    }

    @ParameterizedTest(name = "{0}: normalizeCase:false disables case normalization")
    @MethodSource("textFunctions")
    void normalizeCaseFalseDisablesNormalization(String label, String field, String yqlTemplate) {
        Item root = parse(String.format(yqlTemplate, "{normalizeCase:false}", "a")).getRoot();
        WordItem word = getFirstWord(root);
        assertTrue(word.isLowercased(), "normalizeCase:false should mark word as pre-lowercased (isLowercased=true)");
    }

    @ParameterizedTest(name = "{0}: accentDrop:false disables accent dropping")
    @MethodSource("textFunctions")
    void accentDropFalseDisablesAccentDrop(String label, String field, String yqlTemplate) {
        Item root = parse(String.format(yqlTemplate, "{accentDrop:false}", "a")).getRoot();
        WordItem word = getFirstWord(root);
        assertFalse(word.isNormalizable(), "accentDrop:false should set isNormalizable=false");
    }

    @ParameterizedTest(name = "{0}: usePositionData:false disables position data")
    @MethodSource("textFunctions")
    void usePositionDataFalseDisablesPositionData(String label, String field, String yqlTemplate) {
        Item root = parse(String.format(yqlTemplate, "{usePositionData:false}", "a")).getRoot();
        WordItem word = getFirstWord(root);
        assertFalse(word.usePositionData(), "usePositionData:false should set usePositionData=false");
    }

    static Stream<Arguments> textFunctionsForNear() {
        return Stream.of(
                // text() already defaults to linguistics tokenization, just override composite to near
                Arguments.of("text",
                        "select foo from bar where title contains ({grammar.composite:\"near\",distance:3} text(\"a b\"))"),
                // userInput() needs explicit tokenization override for near to work with linguistics
                Arguments.of("userInput",
                        "select foo from bar where {grammar.syntax:\"none\",grammar.tokenization:\"linguistics\",grammar.composite:\"near\",distance:3} userInput(\"a b\")")
        );
    }

    @ParameterizedTest(name = "{0}: distance annotation sets distance on NearItem")
    @MethodSource("textFunctionsForNear")
    void distanceSetsNearDistance(String label, String yql) {
        Item root = parse(yql).getRoot();
        assertInstanceOf(NearItem.class, root);
        NearItem near = (NearItem) root;
        assertEquals(3, near.getDistance());
        assertEquals(2, near.getItemCount());
    }

    // --- text()-specific tests ---

    @Test
    void testTextDefaultsToLinguisticsMode() {
        Item root = parse("select foo from bar where title contains text(\"yoni jo dima\")").getRoot();
        assertInstanceOf(WeakAndItem.class, root);
        assertEquals("WEAKAND(100) title:yoni title:jo title:dima", root.toString());
        for (Item child : ((WeakAndItem) root).items()) {
            assertInstanceOf(WordItem.class, child);
            WordItem childWord = (WordItem) child;
            assertTrue(childWord.isStemmed());
            assertFalse(childWord.isNormalizable());
            assertTrue(childWord.isLowercased());
        }
    }

    @Test
    void testTextIgnoresDefaultIndexAndUsesContainsField() {
        Item root = parse("select foo from bar where title contains ({defaultIndex:\"other\"}text(\"a b\"))").getRoot();
        assertInstanceOf(WeakAndItem.class, root);
        assertEquals("WEAKAND(100) title:a title:b", root.toString());
    }

    @Test
    void testTextWithPropertyReference() {
        Item root = parse("select foo from bar where title contains text(@q)", "q", "hello world").getRoot();
        assertInstanceOf(WeakAndItem.class, root);
        assertEquals("WEAKAND(100) title:hello title:world", root.toString());
    }

    @Test
    void testTextOutsideContainsFails() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("select foo from bar where text(\"a b\")"));
    }

    // --- Helpers ---

    /**
     * Asserts that root is a CompositeItem of the expected type, containing the
     * expected number of WordItems all indexed against the expected field.
     */
    private static void assertCompositeOfWords(Item root, Class<? extends CompositeItem> expectedType,
                                               String expectedField, int expectedChildren) {
        assertInstanceOf(expectedType, root);
        CompositeItem composite = (CompositeItem) root;
        assertEquals(expectedChildren, composite.getItemCount());
        for (int i = 0; i < composite.getItemCount(); i++) {
            assertInstanceOf(WordItem.class, composite.getItem(i));
            assertEquals(expectedField, ((WordItem) composite.getItem(i)).getIndexName());
        }
    }

    /** Extracts the first WordItem from the root, whether it's a composite or a single word. */
    private static WordItem getFirstWord(Item root) {
        if (root instanceof CompositeItem composite) {
            assertInstanceOf(WordItem.class, composite.getItem(0));
            return (WordItem) composite.getItem(0);
        }
        assertInstanceOf(WordItem.class, root);
        return (WordItem) root;
    }

    private QueryTree parse(String yqlQuery) {
        return parser.parse(new Parsable().setQuery(yqlQuery));
    }

    private QueryTree parse(String yqlQuery, String key, String value) {
        Query userQuery = new Query();
        userQuery.properties().set(key, value);
        parser.setUserQuery(userQuery);
        return parse(yqlQuery);
    }
}
