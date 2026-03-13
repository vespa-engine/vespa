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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the text() function in YQL.
 */
public class TextInputTestCase {

    private YqlParser parser;

    @BeforeEach
    public void setUp() {
        parser = new YqlParser(new ParserEnvironment());
    }

    @Test
    void grammarRawProducesExactStringItem() {
        Item root = parse("select foo from bar where title contains ({grammar:\"raw\"} text(\"a b\"))").getRoot();
        assertInstanceOf(ExactStringItem.class, root);
        WordItem word = (WordItem) root;
        assertEquals("title", word.getIndexName());
        assertEquals("a b", word.getWord());
    }

    @Test
    void grammarAllProducesAnd() {
        Item root = parse("select foo from bar where title contains ({grammar:\"all\"} text(\"a b\"))").getRoot();
        assertCompositeOfWords(root, AndItem.class, "title", 2);
    }

    @Test
    void grammarAnyProducesOr() {
        Item root = parse("select foo from bar where title contains ({grammar:\"any\"} text(\"a b\"))").getRoot();
        assertCompositeOfWords(root, OrItem.class, "title", 2);
    }

    @Test
    void grammarWeakAndProducesWeakAnd() {
        Item root = parse("select foo from bar where title contains ({grammar:\"weakAnd\"} text(\"a b\"))").getRoot();
        assertCompositeOfWords(root, WeakAndItem.class, "title", 2);
    }

    @Test
    void allowEmptyReturnsNullItem() {
        Item root = parse("select foo from bar where title contains ([{allowEmpty:true}] text(@q))", "q", "").getRoot();
        assertInstanceOf(NullItem.class, root);
    }

    @Test
    void targetHitsSetsWeakAndN() {
        Item root = parse("select foo from bar where title contains ({targetHits:50} text(\"a b\"))").getRoot();
        assertInstanceOf(WeakAndItem.class, root);
        WeakAndItem weakAnd = (WeakAndItem) root;
        assertEquals(50, weakAnd.getTargetHits());
        assertEquals(2, weakAnd.getItemCount());
    }

    @Test
    void languageAnnotationSetsLanguage() {
        Item root = parse("select foo from bar where title contains ({language:\"ja\"} text(\"\u30ab\u30bf\u30ab\u30ca\"))").getRoot();
        assertEquals(Language.JAPANESE, root.getLanguage());
    }

    @Test
    void grammarCompositeAndProducesAnd() {
        Item root = parse("select foo from bar where title contains ({grammar.composite:\"and\"} text(\"a b\"))").getRoot();
        assertCompositeOfWords(root, AndItem.class, "title", 2);
    }

    @Test
    void grammarCompositeOrProducesOr() {
        Item root = parse("select foo from bar where title contains ({grammar.composite:\"or\"} text(\"a b\"))").getRoot();
        assertCompositeOfWords(root, OrItem.class, "title", 2);
    }

    @Test
    void stemFalseDisablesStemming() {
        Item root = parse("select foo from bar where title contains ({stem:false} text(\"a\"))").getRoot();
        WordItem word = getFirstWord(root);
        assertTrue(word.isStemmed(), "stem:false should mark word as pre-stemmed (isStemmed=true)");
    }

    @Test
    void rankedFalseSetsUnranked() {
        Item root = parse("select foo from bar where title contains ({ranked:false} text(\"a\"))").getRoot();
        WordItem word = getFirstWord(root);
        assertFalse(word.isRanked(), "ranked:false should set isRanked=false");
    }

    @Test
    void filterTrueSetsFilter() {
        Item root = parse("select foo from bar where title contains ({filter:true} text(\"a\"))").getRoot();
        WordItem word = getFirstWord(root);
        assertTrue(word.isFilter(), "filter:true should set isFilter=true");
    }

    @Test
    void normalizeCaseFalseDisablesNormalization() {
        Item root = parse("select foo from bar where title contains ({normalizeCase:false} text(\"a\"))").getRoot();
        WordItem word = getFirstWord(root);
        assertTrue(word.isLowercased(), "normalizeCase:false should mark word as pre-lowercased (isLowercased=true)");
    }

    @Test
    void accentDropFalseDisablesAccentDrop() {
        Item root = parse("select foo from bar where title contains ({accentDrop:false} text(\"a\"))").getRoot();
        WordItem word = getFirstWord(root);
        assertFalse(word.isNormalizable(), "accentDrop:false should set isNormalizable=false");
    }

    @Test
    void usePositionDataFalseDisablesPositionData() {
        Item root = parse("select foo from bar where title contains ({usePositionData:false} text(\"a\"))").getRoot();
        WordItem word = getFirstWord(root);
        assertFalse(word.usePositionData(), "usePositionData:false should set usePositionData=false");
    }

    @Test
    void distanceSetsNearDistance() {
        Item root = parse("select foo from bar where title contains ({grammar.composite:\"near\",distance:3} text(\"a b\"))").getRoot();
        assertInstanceOf(NearItem.class, root);
        NearItem near = (NearItem) root;
        assertEquals(3, near.getDistance());
        assertEquals(2, near.getItemCount());
    }

    @Test
    void textDefaultsToLinguisticsMode() {
        Item root = parse("select foo from bar where title contains text(\"yoni jo dima\")").getRoot();
        assertInstanceOf(WeakAndItem.class, root);
        assertEquals("WEAKAND title:yoni title:jo title:dima", root.toString());
        for (Item child : ((WeakAndItem) root).items()) {
            assertInstanceOf(WordItem.class, child);
            WordItem childWord = (WordItem) child;
            assertTrue(childWord.isStemmed());
            assertFalse(childWord.isNormalizable());
            assertTrue(childWord.isLowercased());
        }
    }

    @Test
    void textIgnoresDefaultIndexAndUsesContainsField() {
        Item root = parse("select foo from bar where title contains ({defaultIndex:\"other\"}text(\"a b\"))").getRoot();
        assertInstanceOf(WeakAndItem.class, root);
        assertEquals("WEAKAND title:a title:b", root.toString());
    }

    @Test
    void textWithPropertyReference() {
        Item root = parse("select foo from bar where title contains text(@q)", "q", "hello world").getRoot();
        assertInstanceOf(WeakAndItem.class, root);
        assertEquals("WEAKAND title:hello title:world", root.toString());
    }

    @Test
    void textOutsideContainsFails() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("select foo from bar where text(\"a b\")"));
    }

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
