// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.language.Language;
import com.yahoo.language.simple.SimpleToken;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.FieldSet;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the text() function in YQL.
 */
public class TextInputTestCase {

    @Test
    void grammarRawProducesExactStringItem() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({grammar:\"raw\"} text(\"a b\"))").getRoot();
        assertInstanceOf(ExactStringItem.class, root);
        WordItem word = (WordItem) root;
        assertEquals("title", word.getIndexName());
        assertEquals("a b", word.getWord());
    }

    @Test
    void grammarAllProducesAnd() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({grammar:\"all\"} text(\"a b\"))").getRoot();
        tester.assertCompositeOfWords(root, AndItem.class, "title", 2);
    }

    @Test
    void grammarAnyProducesOr() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({grammar:\"any\"} text(\"a b\"))").getRoot();
        tester.assertCompositeOfWords(root, OrItem.class, "title", 2);
    }

    @Test
    void grammarWeakAndProducesWeakAnd() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({grammar:\"weakAnd\"} text(\"a b\"))").getRoot();
        tester.assertCompositeOfWords(root, WeakAndItem.class, "title", 2);
    }

    @Test
    void labelIsPropagatedToParsedTerms() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({label:\"t1\"} text(\"new york\"))").getRoot();
        assertInstanceOf(WeakAndItem.class, root);
        WeakAndItem weakAnd = (WeakAndItem) root;
        assertEquals(2, weakAnd.getItemCount());
        for (Item term : weakAnd.items()) {
            assertEquals("t1", term.getLabel());
        }
    }

    @Test
    void allowEmptyReturnsNullItem() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ([{allowEmpty:true}] text(@q))", "q", "").getRoot();
        assertInstanceOf(NullItem.class, root);
    }

    @Test
    void targetHitsSetsWeakAndN() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({targetHits:50} text(\"a b\"))").getRoot();
        assertInstanceOf(WeakAndItem.class, root);
        WeakAndItem weakAnd = (WeakAndItem) root;
        assertEquals(50, weakAnd.getTargetHits());
        assertEquals(2, weakAnd.getItemCount());
    }

    @Test
    void languageAnnotationSetsLanguage() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({language:\"ja\"} text(\"\u30ab\u30bf\u30ab\u30ca\"))").getRoot();
        assertEquals(Language.JAPANESE, root.getLanguage());
    }

    @Test
    void grammarCompositeAndProducesAnd() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({grammar.composite:\"and\"} text(\"a b\"))").getRoot();
        tester.assertCompositeOfWords(root, AndItem.class, "title", 2);
    }

    @Test
    void grammarCompositeOrProducesOr() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({grammar.composite:\"or\"} text(\"a b\"))").getRoot();
        tester.assertCompositeOfWords(root, OrItem.class, "title", 2);
    }

    @Test
    void stemFalseDisablesStemming() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({stem:false} text(\"a\"))").getRoot();
        WordItem word = tester.getFirstWord(root);
        assertTrue(word.isStemmed(), "stem:false should mark word as pre-stemmed (isStemmed=true)");
    }

    @Test
    void rankedFalseSetsUnranked() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({ranked:false} text(\"a\"))").getRoot();
        WordItem word = tester.getFirstWord(root);
        assertFalse(word.isRanked(), "ranked:false should set isRanked=false");
    }

    @Test
    void filterTrueSetsFilter() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({filter:true} text(\"a\"))").getRoot();
        WordItem word = tester.getFirstWord(root);
        assertTrue(word.isFilter(), "filter:true should set isFilter=true");
    }

    @Test
    void normalizeCaseFalseDisablesNormalization() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({normalizeCase:false} text(\"a\"))").getRoot();
        WordItem word = tester.getFirstWord(root);
        assertTrue(word.isLowercased(), "normalizeCase:false should mark word as pre-lowercased (isLowercased=true)");
    }

    @Test
    void accentDropFalseDisablesAccentDrop() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({accentDrop:false} text(\"a\"))").getRoot();
        WordItem word = tester.getFirstWord(root);
        assertFalse(word.isNormalizable(), "accentDrop:false should set isNormalizable=false");
    }

    @Test
    void usePositionDataFalseDisablesPositionData() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({usePositionData:false} text(\"a\"))").getRoot();
        WordItem word = tester.getFirstWord(root);
        assertFalse(word.usePositionData(), "usePositionData:false should set usePositionData=false");
    }

    @Test
    void distanceSetsNearDistance() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({grammar.composite:\"near\",distance:3} text(\"a b\"))").getRoot();
        assertInstanceOf(NearItem.class, root);
        NearItem near = (NearItem) root;
        assertEquals(3, near.getDistance());
        assertEquals(2, near.getItemCount());
    }

    @Test
    void textDefaultsToLinguisticsMode() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains text(\"yoni jo dima\")").getRoot();
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
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains ({defaultIndex:\"other\"}text(\"a b\"))").getRoot();
        assertInstanceOf(WeakAndItem.class, root);
        assertEquals("WEAKAND title:a title:b", root.toString());
    }

    @Test
    void textWithPropertyReference() {
        var tester = new LinguisticsParserTester();
        Item root = tester.parse("select foo from bar where title contains text(@q)", "q", "hello world").getRoot();
        assertInstanceOf(WeakAndItem.class, root);
        assertEquals("WEAKAND title:hello title:world", root.toString());
    }

    @Test
    void textOutsideContainsFails() {
        var tester = new LinguisticsParserTester();
        assertThrows(IllegalArgumentException.class, () -> tester.parse("select foo from bar where text(\"a b\")"));
    }

    @Test
    void missingInputCausesIllegalInputException() {
        var tester = new LinguisticsParserTester();
        try {
            tester.parse("select foo from bar where title contains text(@missing)", "dummy", "foo").getRoot();
            fail("Expected exception");
        }
        catch (IllegalInputException e) {
            assertEquals("Input 'missing' is not set", Exceptions.toMessageString(e));
        }
    }

    @Test
    void fieldSetWithMultipleProfiles() {
        var schema = new Schema.Builder("schema1");
        schema.add(new Field.Builder("field1", "string").build());
        schema.add(new Field.Builder("field2", "string").build());
        schema.add(new FieldSet.Builder("fieldSet1").addField("field1").addField("field2").build());

        var sd = new SearchDefinition("schema1");
        sd.addIndex(new Index("fieldSet1"));
        var index1 = new Index("field1");
        index1.setLinguisticsProfile("profile1");
        sd.addIndex(index1);
        var index2 = new Index("field2");
        index2.setLinguisticsProfile("profile2");
        sd.addIndex(index2);

        var tester = new LinguisticsParserTester(new SchemaInfo(List.of(schema.build()), List.of()),
                                                 new IndexFacts(new IndexModel(sd)));
        tester.assertParsed("AND (OR field1:a field2:a)",
                            "select * from schema1 where fieldSet1 contains ({grammar.composite:'and'}text('a'))");
        tester.assertParsed("AND (OR field1:a field2:a) (OR field1:b field2:b)",
                            "select * from schema1 where fieldSet1 contains ({grammar.composite:'and'}text('a b'))");

        // Different tokenization
        tester.tokenizer().putTokens("profile1", "query text", "a", "b");
        tester.tokenizer().putTokens("profile2", "query text", "c", "d");
        tester.assertParsed("AND (OR field1:a field2:c) (OR field1:b field2:d)",
                            "select * from schema1 where fieldSet1 contains ({grammar.composite:'and'}text('query text'))");

        // Different tokenization length
        tester.tokenizer().putTokens("profile1", "query text", "a");
        tester.tokenizer().putTokens("profile2", "query text", "c", "d");
        tester.assertParsed("AND (OR field1:a field2:c) field2:d",
                            "select * from schema1 where fieldSet1 contains ({grammar.composite:'and'}text('query text'))");

        // Multiple alternatives at the same position
        tester.tokenizer().putTokens("profile1", "query text", "a");
        tester.tokenizer().putTokens("profile2", "query text", SimpleToken.fromStems("a", List.of("c1", "c2")),
                                                                            SimpleToken.fromStems("d", List.of("d1", "d2")));
        tester.assertParsed("AND (OR field1:a WORD_ALTERNATIVES field2:[ c1(1.0) c2(1.0) ]) WORD_ALTERNATIVES field2:[ d1(1.0) d2(1.0) ]",
                            "select * from schema1 where fieldSet1 contains ({grammar.composite:'and'}text('query text'))");
    }

}
