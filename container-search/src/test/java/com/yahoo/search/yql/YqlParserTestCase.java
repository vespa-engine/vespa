// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.component.Version;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.language.Language;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.IndexedItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.RegExpItem;
import com.yahoo.prelude.query.SegmentingRule;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.SubstringItem;
import com.yahoo.prelude.query.SuffixItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordAlternativesItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.IndexInfoConfig.Indexinfo;
import com.yahoo.search.config.IndexInfoConfig.Indexinfo.Alias;
import com.yahoo.search.config.IndexInfoConfig.Indexinfo.Command;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.Sorting.AttributeSorter;
import com.yahoo.search.query.Sorting.FieldOrder;
import com.yahoo.search.query.Sorting.LowerCaseSorter;
import com.yahoo.search.query.Sorting.Order;
import com.yahoo.search.query.Sorting.UcaSorter;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Specification for the conversion of YQL+ expressions to Vespa search queries.
 *
 * @author steinar
 * @author stiankri
 */
public class YqlParserTestCase {

    private final YqlParser parser = new YqlParser(new ParserEnvironment());

    @Test
    public void requireThatDefaultsAreSane() {
        assertTrue(parser.isQueryParser());
        assertNull(parser.getDocTypes());
    }
    
    @Test
    public void testLanguageDetection() {
        // SimpleDetector used here can detect japanese and will set that as language at the root of the user input
        QueryTree tree = parse("select * from sources * where userInput(\"\u30ab\u30bf\u30ab\u30ca\");");
        assertEquals(Language.JAPANESE, tree.getRoot().getLanguage());
    }

    @Test
    public void requireThatGroupingStepCanBeParsed() {
        assertParse("select foo from bar where baz contains 'cox';",
                    "baz:cox");
        assertEquals("[]",
                     toString(parser.getGroupingSteps()));

        assertParse("select foo from bar where baz contains 'cox' " +
                    "| all(group(a) each(output(count())));",
                    "baz:cox");
        assertEquals("[[]all(group(a) each(output(count())))]",
                     toString(parser.getGroupingSteps()));

        assertParse("select foo from bar where baz contains 'cox' " +
                    "| all(group(a) each(output(count()))) " +
                    "| all(group(b) each(output(count())));",
                    "baz:cox");
        assertEquals("[[]all(group(a) each(output(count())))," +
                     " []all(group(b) each(output(count())))]",
                     toString(parser.getGroupingSteps()));
    }

    @Test
    public void requireThatGroupingContinuationCanBeParsed() {
        assertParse("select foo from bar where baz contains 'cox' " +
                    "| [{ 'continuations': ['BCBCBCBEBG', 'BCBKCBACBKCCK'] }]all(group(a) each(output(count())));",
                    "baz:cox");
        assertEquals("[[BCBCBCBEBG, BCBKCBACBKCCK]all(group(a) each(output(count())))]",
                     toString(parser.getGroupingSteps()));

        assertParse("select foo from bar where baz contains 'cox' " +
                    "| [{ 'continuations': ['BCBCBCBEBG', 'BCBKCBACBKCCK'] }]all(group(a) each(output(count()))) " +
                    "| [{ 'continuations': ['BCBBBBBDBF', 'BCBJBPCBJCCJ'] }]all(group(b) each(output(count())));",
                    "baz:cox");
        assertEquals("[[BCBCBCBEBG, BCBKCBACBKCCK]all(group(a) each(output(count())))," +
                     " [BCBBBBBDBF, BCBJBPCBJCCJ]all(group(b) each(output(count())))]",
                     toString(parser.getGroupingSteps()));
    }

    @Test
    public void test() {
        assertParse("select foo from bar where title contains \"madonna\";",
                    "title:madonna");
    }

    @Test
    public void testDottedFieldNames() {
        assertParse("select foo from bar where my.title contains \"madonna\";",
                "my.title:madonna");
    }

    @Test
    public void testOr() {
        assertParse("select foo from bar where title contains \"madonna\" or title contains \"saint\";",
                    "OR title:madonna title:saint");
        assertParse("select foo from bar where title contains \"madonna\" or title contains \"saint\" or title " +
                    "contains \"angel\";",
                    "OR title:madonna title:saint title:angel");
    }

    @Test
    public void testAnd() {
        assertParse("select foo from bar where title contains \"madonna\" and title contains \"saint\";",
                    "AND title:madonna title:saint");
        assertParse("select foo from bar where title contains \"madonna\" and title contains \"saint\" and title " +
                    "contains \"angel\";",
                    "AND title:madonna title:saint title:angel");
    }

    @Test
    public void testAndNot() {
        assertParse("select foo from bar where title contains \"madonna\" and !(title contains \"saint\");",
                    "+title:madonna -title:saint");
    }

    @Test
    public void testLessThan() {
        assertParse("select foo from bar where price < 500;", "price:<500");
        assertParse("select foo from bar where 500 < price;", "price:>500");
    }

    @Test
    public void testGreaterThan() {
        assertParse("select foo from bar where price > 500;", "price:>500");
        assertParse("select foo from bar where 500 > price;", "price:<500");
    }

    @Test
    public void testLessThanOrEqual() {
        assertParse("select foo from bar where price <= 500;", "price:[;500]");
        assertParse("select foo from bar where 500 <= price;", "price:[500;]");
    }

    @Test
    public void testGreaterThanOrEqual() {
        assertParse("select foo from bar where price >= 500;", "price:[500;]");
        assertParse("select foo from bar where 500 >= price;", "price:[;500]");
    }

    @Test
    public void testEquality() {
        assertParse("select foo from bar where price = 500;", "price:500");
        assertParse("select foo from bar where 500 = price;", "price:500");
    }

    @Test
    public void testNegativeLessThan() {
        assertParse("select foo from bar where price < -500;", "price:<-500");
        assertParse("select foo from bar where -500 < price;", "price:>-500");
    }

    @Test
    public void testNegativeGreaterThan() {
        assertParse("select foo from bar where price > -500;", "price:>-500");
        assertParse("select foo from bar where -500 > price;", "price:<-500");
    }

    @Test
    public void testNegativeLessThanOrEqual() {
        assertParse("select foo from bar where price <= -500;", "price:[;-500]");
        assertParse("select foo from bar where -500 <= price;", "price:[-500;]");
    }

    @Test
    public void testNegativeGreaterThanOrEqual() {
        assertParse("select foo from bar where price >= -500;", "price:[-500;]");
        assertParse("select foo from bar where -500 >= price;", "price:[;-500]");
    }

    @Test
    public void testNegativeEquality() {
        assertParse("select foo from bar where price = -500;", "price:-500");
        assertParse("select foo from bar where -500 = price;", "price:-500");
    }

    @Test
    public void testAnnotatedLessThan() {
        assertParse("select foo from bar where price < ([{\"filter\": true}](-500));", "|price:<-500");
        assertParse("select foo from bar where ([{\"filter\": true}]500) < price;", "|price:>500");
    }

    @Test
    public void testAnnotatedGreaterThan() {
        assertParse("select foo from bar where price > ([{\"filter\": true}]500);", "|price:>500");
        assertParse("select foo from bar where ([{\"filter\": true}](-500)) > price;", "|price:<-500");
    }

    @Test
    public void testAnnotatedLessThanOrEqual() {
        assertParse("select foo from bar where price <= ([{\"filter\": true}](-500));", "|price:[;-500]");
        assertParse("select foo from bar where ([{\"filter\": true}]500) <= price;", "|price:[500;]");
    }

    @Test
    public void testAnnotatedGreaterThanOrEqual() {
        assertParse("select foo from bar where price >= ([{\"filter\": true}]500);", "|price:[500;]");
        assertParse("select foo from bar where ([{\"filter\": true}](-500)) >= price;", "|price:[;-500]");
    }

    @Test
    public void testAnnotatedEquality() {
        assertParse("select foo from bar where price = ([{\"filter\": true}](-500));", "|price:-500");
        assertParse("select foo from bar where ([{\"filter\": true}]500) = price;", "|price:500");
    }

    @Test
    public void testTermAnnotations() {
        assertEquals("merkelapp",
                     getRootWord("select foo from bar where baz contains " +
                                 "([ {\"label\": \"merkelapp\"} ]\"colors\");").getLabel());
        assertEquals("another",
                     getRootWord("select foo from bar where baz contains " +
                                 "([ {\"annotations\": {\"cox\": \"another\"}} ]\"colors\");").getAnnotation("cox"));
        assertEquals(23.0, getRootWord("select foo from bar where baz contains " +
                                       "([ {\"significance\": 23.0} ]\"colors\");").getSignificance(), 1E-6);
        assertEquals(23, getRootWord("select foo from bar where baz contains " +
                                     "([ {\"id\": 23} ]\"colors\");").getUniqueID());
        assertEquals(150, getRootWord("select foo from bar where baz contains " +
                                      "([ {\"weight\": 150} ]\"colors\");").getWeight());
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "([ {\"usePositionData\": false} ]\"colors\");").usePositionData());
        assertTrue(getRootWord("select foo from bar where baz contains " +
                               "([ {\"filter\": true} ]\"colors\");").isFilter());
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "([ {\"ranked\": false} ]\"colors\");").isRanked());

        Substring origin = getRootWord("select foo from bar where baz contains " +
                                       "([ {\"origin\": {\"original\": \"abc\", \"offset\": 1, \"length\": 2}} ]" +
                                       "\"colors\");").getOrigin();
        assertEquals("abc", origin.string);
        assertEquals(1, origin.start);
        assertEquals(3, origin.end);
    }

    @Test
    public void testSameElement() {
        assertParse("select foo from bar where baz contains sameElement(f1 contains \"a\", f2 contains \"b\");",
                "baz:{f1:a f2:b}");
        assertParse("select foo from bar where baz contains sameElement(f1 contains \"a\", f2 = 10);",
                "baz:{f1:a f2:10}");
        assertParse("select foo from bar where baz contains sameElement(key contains \"a\", value.f2 = 10);",
                "baz:{key:a value.f2:10}");
    }

    @Test
    public void testPhrase() {
        assertParse("select foo from bar where baz contains phrase(\"a\", \"b\");",
                    "baz:\"a b\"");
    }

    @Test
    public void testNestedPhrase() {
        assertParse("select foo from bar where baz contains phrase(\"a\", \"b\", phrase(\"c\", \"d\"));",
                    "baz:\"a b c d\"");
    }

    @Test
    public void testNestedPhraseSegment() {
        assertParse("select foo from bar where baz contains " +
                    "phrase(\"a\", \"b\", [ {\"origin\": {\"original\": \"c d\", \"offset\": 0, \"length\": 3}} ]" +
                    "phrase(\"c\", \"d\"));",
                    "baz:\"a b 'c d'\"");
    }

    @Test
    public void testStemming() {
        assertTrue(getRootWord("select foo from bar where baz contains " +
                               "([ {\"stem\": false} ]\"colors\");").isStemmed());
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "([ {\"stem\": true} ]\"colors\");").isStemmed());
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "\"colors\";").isStemmed());
    }

    @Test
    public void testRaw() {
        Item root = parse("select foo from bar where baz contains (\"yoni jo dima\");").getRoot();
        assertTrue(root instanceof WordItem);
        assertFalse(root instanceof ExactStringItem);
        assertEquals("yoni jo dima", ((WordItem)root).getWord());

        root = parse("select foo from bar where baz contains ([{\"grammar\":\"raw\"}]\"yoni jo dima\");").getRoot();
        assertTrue(root instanceof WordItem);
        assertFalse(root instanceof ExactStringItem);
        assertEquals("yoni jo dima", ((WordItem)root).getWord());

        root = parse("select foo from bar where userInput(\"yoni jo dima\");").getRoot();
        assertTrue(root instanceof AndItem);
        AndItem andItem = (AndItem) root;
        assertEquals(3, andItem.getItemCount());

        root = parse("select foo from bar where [{\"grammar\":\"raw\"}]userInput(\"yoni jo dima\");").getRoot();
        assertTrue(root instanceof WordItem);
        assertTrue(root instanceof ExactStringItem);
        assertEquals("yoni jo dima", ((WordItem)root).getWord());
    }

    @Test
    public void testAccentDropping() {
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "([ {\"accentDrop\": false} ]\"colors\");").isNormalizable());
        assertTrue(getRootWord("select foo from bar where baz contains " +
                               "([ {\"accentDrop\": true} ]\"colors\");").isNormalizable());
        assertTrue(getRootWord("select foo from bar where baz contains " +
                               "\"colors\";").isNormalizable());
    }

    @Test
    public void testCaseNormalization() {
        assertTrue(getRootWord("select foo from bar where baz contains " +
                               "([ {\"normalizeCase\": false} ]\"colors\");").isLowercased());
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "([ {\"normalizeCase\": true} ]\"colors\");").isLowercased());
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "\"colors\";").isLowercased());
    }

    @Test
    public void testSegmentingRule() {
        assertEquals(SegmentingRule.PHRASE,
                     getRootWord("select foo from bar where baz contains " +
                                 "([ {\"andSegmenting\": false} ]\"colors\");").getSegmentingRule());
        assertEquals(SegmentingRule.BOOLEAN_AND,
                     getRootWord("select foo from bar where baz contains " +
                                 "([ {\"andSegmenting\": true} ]\"colors\");").getSegmentingRule());
        assertEquals(SegmentingRule.LANGUAGE_DEFAULT,
                     getRootWord("select foo from bar where baz contains " +
                                 "\"colors\";").getSegmentingRule());
    }

    @Test
    public void testNfkc() {
        assertEquals("a\u030a",
                     getRootWord("select foo from bar where baz contains " +
                                 "([ {\"nfkc\": false} ]\"a\\u030a\");").getWord());
        assertEquals("\u00e5",
                     getRootWord("select foo from bar where baz contains " +
                                 "([ {\"nfkc\": true} ]\"a\\u030a\");").getWord());
        assertEquals("No NKFC by default", 
                     "a\u030a",
                     getRootWord("select foo from bar where baz contains " +
                                 "(\"a\\u030a\");").getWord());
    }

    @Test
    public void testImplicitTransforms() {
        assertFalse(getRootWord("select foo from bar where baz contains ([ {\"implicitTransforms\": " +
                                "false} ]\"cox\");").isFromQuery());
        assertTrue(getRootWord("select foo from bar where baz contains ([ {\"implicitTransforms\": " +
                               "true} ]\"cox\");").isFromQuery());
        assertTrue(getRootWord("select foo from bar where baz contains \"cox\";").isFromQuery());
    }

    @Test
    public void testConnectivity() {
        QueryTree parsed = parse("select foo from bar where " +
                                 "title contains ([{\"id\": 1, \"connectivity\": {\"id\": 3, \"weight\": 7.0}}]\"madonna\") " +
                                 "and title contains ([{\"id\": 2}]\"saint\") " +
                                 "and title contains ([{\"id\": 3}]\"angel\");");
        assertEquals("AND title:madonna title:saint title:angel",
                     parsed.toString());
        AndItem root = (AndItem)parsed.getRoot();
        WordItem first = (WordItem)root.getItem(0);
        WordItem second = (WordItem)root.getItem(1);
        WordItem third = (WordItem)root.getItem(2);
        assertTrue(first.getConnectedItem() == third);
        assertEquals(first.getConnectivity(), 7.0d, 1E-6);
        assertNull(second.getConnectedItem());

        assertParseFail("select foo from bar where " +
                        "title contains ([{\"id\": 1, \"connectivity\": {\"id\": 4, \"weight\": 7.0}}]\"madonna\") " +
                        "and title contains ([{\"id\": 2}]\"saint\") " +
                        "and title contains ([{\"id\": 3}]\"angel\");",
                        new NullPointerException("Item 'title:madonna' was specified to connect to item with ID 4, " +
                                                 "which does not exist in the query."));
    }

    @Test
    public void testAnnotatedPhrase() {
        QueryTree parsed =
                parse("select foo from bar where baz contains ([{\"label\": \"hello world\"}]phrase(\"a\", \"b\"));");
        assertEquals("baz:\"a b\"", parsed.toString());
        PhraseItem phrase = (PhraseItem)parsed.getRoot();
        assertEquals("hello world", phrase.getLabel());
    }

    @Test
    public void testRange() {
        QueryTree parsed = parse("select foo from bar where range(baz,1,8);");
        assertEquals("baz:[1;8]", parsed.toString());
    }

    @Test
    public void testNegativeRange() {
        QueryTree parsed = parse("select foo from bar where range(baz,-8,-1);");
        assertEquals("baz:[-8;-1]", parsed.toString());
    }

    @Test
    public void testRangeIllegalArguments() {
        assertParseFail("select foo from bar where range(baz,cox,8);",
                        new IllegalArgumentException("Expected operator LITERAL, got READ_FIELD."));
    }

    @Test
    public void testNear() {
        assertParse("select foo from bar where description contains near(\"a\", \"b\");",
                    "NEAR(2) description:a description:b");
        assertParse("select foo from bar where description contains ([ {\"distance\": 100} ]near(\"a\", \"b\"));",
                    "NEAR(100) description:a description:b");
    }

    @Test
    public void testOrderedNear() {
        assertParse("select foo from bar where description contains onear(\"a\", \"b\");",
                    "ONEAR(2) description:a description:b");
        assertParse("select foo from bar where description contains ([ {\"distance\": 100} ]onear(\"a\", \"b\"));",
                    "ONEAR(100) description:a description:b");
    }

    //This test is order dependent. Fix this!!
    @Test
    public void testWand() {
        assertParse("select foo from bar where wand(description, {\"a\":1, \"b\":2});",
                "WAND(10,0.0,1.0) description{[1]:\"a\",[2]:\"b\"}");
        assertParse("select foo from bar where [ {\"scoreThreshold\": 13.3, \"targetNumHits\": 7, " +
                    "\"thresholdBoostFactor\": 2.3} ]wand(description, {\"a\":1, \"b\":2});",
                    "WAND(7,13.3,2.3) description{[1]:\"a\",[2]:\"b\"}");
    }

    @Test
    public void testNumericWand() {
        String numWand = "WAND(10,0.0,1.0) description{[1]:\"11\",[2]:\"37\"}";
        assertParse("select foo from bar where wand(description, [[11,1], [37,2]]);", numWand);
        assertParse("select foo from bar where wand(description, [[11L,1], [37L,2]]);", numWand);
        assertParseFail("select foo from bar where wand(description, 12);",
                        new IllegalArgumentException("Expected ARRAY or MAP, got LITERAL."));
    }

    @Test
    //This test is order dependent. Fix it!
    public void testWeightedSet() {
        assertParse("select foo from bar where weightedSet(description, {\"a\":1, \"b\":2});",
                    "WEIGHTEDSET description{[1]:\"a\",[2]:\"b\"}");
        assertParseFail("select foo from bar where weightedSet(description, {\"a\":g, \"b\":2});",
                        new IllegalArgumentException("Expected operator LITERAL, got READ_FIELD."));
        assertParseFail("select foo from bar where weightedSet(description);",
                        new IllegalArgumentException("Expected 2 arguments, got 1."));
    }

    //This test is order dependent. Fix it!
    @Test
    public void testDotProduct() {
        assertParse("select foo from bar where dotProduct(description, {\"a\":1, \"b\":2});",
                    "DOTPRODUCT description{[1]:\"a\",[2]:\"b\"}");
        assertParse("select foo from bar where dotProduct(description, {\"a\":2});",
                    "DOTPRODUCT description{[2]:\"a\"}");
    }

    @Test
    public void testPredicate() {
        assertParse("select foo from bar where predicate(predicate_field, " +
                "{\"gender\":\"male\", \"hobby\":[\"music\", \"hiking\"]}, {\"age\":23L});",
                "PREDICATE_QUERY_ITEM gender=male, hobby=music, hobby=hiking, age:23");
        assertParse("select foo from bar where predicate(predicate_field, " +
                "{\"gender\":\"male\", \"hobby\":[\"music\", \"hiking\"]}, {\"age\":23});",
                "PREDICATE_QUERY_ITEM gender=male, hobby=music, hobby=hiking, age:23");
        assertParse("select foo from bar where predicate(predicate_field, 0, void);",
                "PREDICATE_QUERY_ITEM ");
    }

    @Test
    public void testPredicateWithSubQueries() {
        assertParse("select foo from bar where predicate(predicate_field, " +
                "{\"0x03\":{\"gender\":\"male\"},\"0x01\":{\"hobby\":[\"music\", \"hiking\"]}}, {\"0x80ffffffffffffff\":{\"age\":23L}});",
                "PREDICATE_QUERY_ITEM gender=male[0x3], hobby=music[0x1], hobby=hiking[0x1], age:23[0x80ffffffffffffff]");
        assertParseFail("select foo from bar where predicate(foo, null, {\"0x80000000000000000\":{\"age\":23}});",
                new NumberFormatException("Too long subquery string: 0x80000000000000000"));
        assertParse("select foo from bar where predicate(predicate_field, " +
                        "{\"[0,1]\":{\"gender\":\"male\"},\"[0]\":{\"hobby\":[\"music\", \"hiking\"]}}, {\"[62, 63]\":{\"age\":23L}});",
                "PREDICATE_QUERY_ITEM gender=male[0x3], hobby=music[0x1], hobby=hiking[0x1], age:23[0xc000000000000000]");
    }

    @Test
    public void testRank() {
        assertParse("select foo from bar where rank(a contains \"A\", b contains \"B\");",
                    "RANK a:A b:B");
        assertParse("select foo from bar where rank(a contains \"A\", b contains \"B\", c " +
                    "contains \"C\");",
                    "RANK a:A b:B c:C");
        assertParse("select foo from bar where rank(a contains \"A\", b contains \"B\"  or c " +
                    "contains \"C\");",
                    "RANK a:A (OR b:B c:C)");
    }

    @Test
    public void testWeakAnd() {
        assertParse("select foo from bar where weakAnd(a contains \"A\", b contains \"B\");",
                    "WAND(100) a:A b:B");
        assertParse("select foo from bar where [{\"targetNumHits\": 37}]weakAnd(a contains \"A\", " +
                    "b contains \"B\");",
                    "WAND(37) a:A b:B");

        QueryTree tree = parse("select foo from bar where [{\"scoreThreshold\": 41}]weakAnd(a " +
                               "contains \"A\", b contains \"B\");");
        assertEquals("WAND(100) a:A b:B", tree.toString());
        assertEquals(WeakAndItem.class, tree.getRoot().getClass());
        assertEquals(41, ((WeakAndItem)tree.getRoot()).getScoreThreshold());
    }

    @Test
    public void testEquiv() {
        assertParse("select foo from bar where fieldName contains equiv(\"A\",\"B\");",
                    "EQUIV fieldName:A fieldName:B");
        assertParse("select foo from bar where fieldName contains " +
                    "equiv(\"ny\",phrase(\"new\",\"york\"));",
                    "EQUIV fieldName:ny fieldName:\"new york\"");
        assertParseFail("select foo from bar where fieldName contains equiv(\"ny\");",
                        new IllegalArgumentException("Expected 2 or more arguments, got 1."));
        assertParseFail("select foo from bar where fieldName contains equiv(\"ny\", nalle(void));",
                        new IllegalArgumentException("Expected function 'phrase', got 'nalle'."));
        assertParseFail("select foo from bar where fieldName contains equiv(\"ny\", 42);",
                        new ClassCastException("Cannot cast java.lang.Integer to java.lang.String"));
    }

    @Test
    public void testAffixItems() {
        assertRootClass("select foo from bar where baz contains ([ {\"suffix\": true} ]\"colors\");",
                        SuffixItem.class);
        assertRootClass("select foo from bar where baz contains ([ {\"prefix\": true} ]\"colors\");",
                        PrefixItem.class);
        assertRootClass("select foo from bar where baz contains ([ {\"substring\": true} ]\"colors\");",
                        SubstringItem.class);
        assertParseFail("select foo from bar where description contains ([ {\"suffix\": true, " +
                        "\"prefix\": true} ]\"colors\");",
                        new IllegalArgumentException("Only one of prefix, substring and suffix can be set."));
        assertParseFail("select foo from bar where description contains ([ {\"suffix\": true, " +
                        "\"substring\": true} ]\"colors\");",
                        new IllegalArgumentException("Only one of prefix, substring and suffix can be set."));
    }

    @Test
    public void testLongNumberInSimpleExpression() {
        assertParse("select foo from bar where price = 8589934592L;",
                    "price:8589934592");
    }

    @Test
    public void testNegativeLongNumberInSimpleExpression() {
        assertParse("select foo from bar where price = -8589934592L;",
                "price:-8589934592");
    }

    @Test
    public void testSources() {
        assertSources("select foo from sourceA where price <= 500;",
                      Arrays.asList("sourceA"));
    }

    @Test
    public void testWildCardSources() {
        assertSources("select foo from sources * where price <= 500;",
                      Collections.<String>emptyList());
    }

    @Test
    public void testMultiSources() {
        assertSources("select foo from sources sourceA, sourceB where price <= 500;",
                      Arrays.asList("sourceA", "sourceB"));
    }

    @Test
    public void testFields() {
        assertSummaryFields("select fieldA from bar where price <= 500;",
                            Arrays.asList("fieldA"));
        assertSummaryFields("select fieldA, fieldB from bar where price <= 500;",
                            Arrays.asList("fieldA", "fieldB"));
        assertSummaryFields("select fieldA, fieldB, fieldC from bar where price <= 500;",
                            Arrays.asList("fieldA", "fieldB", "fieldC"));
        assertSummaryFields("select * from bar where price <= 500;",
                            Collections.<String>emptyList());
    }

    @Test
    public void testFieldsRoot() {
        assertParse("select * from bar where price <= 500;",
                    "price:[;500]");
    }

    @Test
    public void testOffset() {
        assertParse("select foo from bar where title contains \"madonna\" offset 37;",
                    "title:madonna");
        assertEquals(Integer.valueOf(37), parser.getOffset());
    }

    @Test
    public void testLimit() {
        assertParse("select foo from bar where title contains \"madonna\" limit 29;",
                    "title:madonna");
        assertEquals(Integer.valueOf(29), parser.getHits());
    }

    @Test
    public void testOffsetAndLimit() {
        assertParse("select foo from bar where title contains \"madonna\" limit 31 offset 29;",
                    "title:madonna");
        assertEquals(Integer.valueOf(29), parser.getOffset());
        assertEquals(Integer.valueOf(2), parser.getHits());

        assertParse("select * from bar where title contains \"madonna\" limit 41 offset 37;",
                    "title:madonna");
        assertEquals(Integer.valueOf(37), parser.getOffset());
        assertEquals(Integer.valueOf(4), parser.getHits());
    }

    @Test
    public void testTimeout() {
        assertParse("select * from bar where title contains \"madonna\" timeout 7;",
                    "title:madonna");
        assertEquals(Integer.valueOf(7), parser.getTimeout());

        assertParse("select foo from bar where title contains \"madonna\" limit 600 timeout 3;",
                    "title:madonna");
        assertEquals(Integer.valueOf(3), parser.getTimeout());
    }

    @Test
    public void testOrdering() {
        assertParse("select foo from bar where title contains \"madonna\" order by something asc, " +
                    "shoesize desc limit 600 timeout 3;",
                    "title:madonna");
        assertEquals(2, parser.getSorting().fieldOrders().size());
        assertEquals("something", parser.getSorting().fieldOrders().get(0).getFieldName());
        assertEquals(Order.ASCENDING, parser.getSorting().fieldOrders().get(0).getSortOrder());
        assertEquals("shoesize", parser.getSorting().fieldOrders().get(1).getFieldName());
        assertEquals(Order.DESCENDING, parser.getSorting().fieldOrders().get(1).getSortOrder());

        assertParse("select foo from bar where title contains \"madonna\" order by other limit 600 " +
                    "timeout 3;",
                    "title:madonna");
        assertEquals("other", parser.getSorting().fieldOrders().get(0).getFieldName());
        assertEquals(Order.ASCENDING, parser.getSorting().fieldOrders().get(0).getSortOrder());
    }

    @Test
    public void testAnnotatedOrdering() {
        assertParse(
                "select foo from bar where title contains \"madonna\""
                        + " order by [{\"function\": \"uca\", \"locale\": \"en_US\", \"strength\": \"IDENTICAL\"}]other desc"
                        + " limit 600" + " timeout 3;", "title:madonna");
        final FieldOrder fieldOrder = parser.getSorting().fieldOrders().get(0);
        assertEquals("other", fieldOrder.getFieldName());
        assertEquals(Order.DESCENDING, fieldOrder.getSortOrder());
        final AttributeSorter sorter = fieldOrder.getSorter();
        assertEquals(UcaSorter.class, sorter.getClass());
        final UcaSorter uca = (UcaSorter) sorter;
        assertEquals("en_US", uca.getLocale());
        assertEquals(UcaSorter.Strength.IDENTICAL, uca.getStrength());
    }

    @Test
    public void testMultipleAnnotatedOrdering() {
        assertParse(
                "select foo from bar where title contains \"madonna\""
                        + " order by [{\"function\": \"uca\", \"locale\": \"en_US\", \"strength\": \"IDENTICAL\"}]other desc,"
                        + " [{\"function\": \"lowercase\"}]something asc"
                        + " limit 600" + " timeout 3;", "title:madonna");
        {
            final FieldOrder fieldOrder = parser.getSorting().fieldOrders()
                    .get(0);
            assertEquals("other", fieldOrder.getFieldName());
            assertEquals(Order.DESCENDING, fieldOrder.getSortOrder());
            final AttributeSorter sorter = fieldOrder.getSorter();
            assertEquals(UcaSorter.class, sorter.getClass());
            final UcaSorter uca = (UcaSorter) sorter;
            assertEquals("en_US", uca.getLocale());
            assertEquals(UcaSorter.Strength.IDENTICAL, uca.getStrength());
        }
        {
            final FieldOrder fieldOrder = parser.getSorting().fieldOrders()
                    .get(1);
            assertEquals("something", fieldOrder.getFieldName());
            assertEquals(Order.ASCENDING, fieldOrder.getSortOrder());
            final AttributeSorter sorter = fieldOrder.getSorter();
            assertEquals(LowerCaseSorter.class, sorter.getClass());
        }
    }

    @Test
    public void testSegmenting() {
        assertParse("select * from bar where ([{\"segmenter\": {\"version\": \"58.67.49\", \"backend\": " +
                    "\"yell\"}}] title contains \"madonna\");",
                    "title:madonna");
        assertEquals("yell", parser.getSegmenterBackend());
        assertEquals(new Version("58.67.49"), parser.getSegmenterVersion());

        assertParse("select * from bar where ([{\"segmenter\": {\"version\": \"8.7.3\", \"backend\": " +
                    "\"yell\"}}]([{\"targetNumHits\": 9999438}] weakAnd(format contains \"online\", title contains " +
                    "\"madonna\")));",
                    "WAND(9999438) format:online title:madonna");
        assertEquals("yell", parser.getSegmenterBackend());
        assertEquals(new Version("8.7.3"), parser.getSegmenterVersion());

        assertParse("select * from bar where [{\"segmenter\": {\"version\": \"18.47.39\", \"backend\": " +
                    "\"yell\"}}] ([{\"targetNumHits\": 99909438}] weakAnd(format contains \"online\", title contains " +
                    "\"madonna\"));",
                    "WAND(99909438) format:online title:madonna");
        assertEquals("yell", parser.getSegmenterBackend());
        assertEquals(new Version("18.47.39"), parser.getSegmenterVersion());

        assertParse("select * from bar where [{\"targetNumHits\": 99909438}] weakAnd(format contains " +
                    "\"online\", title contains \"madonna\");",
                    "WAND(99909438) format:online title:madonna");
        assertNull(parser.getSegmenterBackend());
        assertNull(parser.getSegmenterVersion());

        assertParse("select * from bar where [{\"segmenter\": {\"version\": \"58.67.49\", \"backend\": " +
                    "\"yell\"}}](title contains \"madonna\") order by shoesize;",
                    "title:madonna");
        assertEquals("yell", parser.getSegmenterBackend());
        assertEquals(new Version("58.67.49"), parser.getSegmenterVersion());
    }

    @Test
    public void testNegativeHitLimit() {
        assertParse(
                "select * from sources * where [{\"hitLimit\": -38}]range(foo, 0, 1);",
                "foo:[0;1;-38]");
    }

    @Test
    public void testRangeSearchHitPopulationOrdering() {
        assertParse("select * from sources * where [{\"hitLimit\": 38, \"ascending\": true}]range(foo, 0, 1);", "foo:[0;1;38]");
        assertParse("select * from sources * where [{\"hitLimit\": 38, \"ascending\": false}]range(foo, 0, 1);", "foo:[0;1;-38]");
        assertParse("select * from sources * where [{\"hitLimit\": 38, \"descending\": true}]range(foo, 0, 1);", "foo:[0;1;-38]");
        assertParse("select * from sources * where [{\"hitLimit\": 38, \"descending\": false}]range(foo, 0, 1);", "foo:[0;1;38]");

        boolean gotExceptionFromParse = false;
        try {
            parse("select * from sources * where [{\"hitLimit\": 38, \"ascending\": true, \"descending\": false}]range(foo, 0, 1);");
        } catch (IllegalArgumentException e) {
            assertTrue("Expected information about abuse of settings.",
                    e.getMessage().contains("both ascending and descending ordering set"));
            gotExceptionFromParse = true;
        }
        assertTrue(gotExceptionFromParse);
    }

    @Test
    public void testOpenIntervals() {
        assertParse("select * from sources * where range(title, 0.0, 500.0);",
                "title:[0.0;500.0]");
        assertParse(
                "select * from sources * where [{\"bounds\": \"open\"}]range(title, 0.0, 500.0);",
                "title:<0.0;500.0>");
        assertParse(
                "select * from sources * where [{\"bounds\": \"leftOpen\"}]range(title, 0.0, 500.0);",
                "title:<0.0;500.0]");
        assertParse(
                "select * from sources * where [{\"bounds\": \"rightOpen\"}]range(title, 0.0, 500.0);",
                "title:[0.0;500.0>");
    }

    @Test
    public void testInheritedAnnotations() {
        {
            QueryTree x = parse("select * from sources * where ([{\"ranked\": false}](foo contains \"a\" and bar contains \"b\")) or foor contains ([{\"ranked\": false}]\"c\");");
            List<IndexedItem> terms = QueryTree.getPositiveTerms(x);
            assertEquals(3, terms.size());
            for (IndexedItem term : terms) {
                assertFalse(((Item) term).isRanked());
            }
        }
        {
            QueryTree x = parse("select * from sources * where [{\"ranked\": false}](foo contains \"a\" and bar contains \"b\");");
            List<IndexedItem> terms = QueryTree.getPositiveTerms(x);
            assertEquals(2, terms.size());
            for (IndexedItem term : terms) {
                assertFalse(((Item) term).isRanked());
            }
        }
    }

    @Test
    public void testMoreInheritedAnnotations() {
        final String yqlQuery = "select * from sources * where "
                + "([{\"ranked\": false}](foo contains \"a\" "
                + "and ([{\"ranked\": true}](bar contains \"b\" "
                + "or ([{\"ranked\": false}](foo contains \"c\" "
                + "and foo contains ([{\"ranked\": true}]\"d\")))))));";
        QueryTree x = parse(yqlQuery);
        List<IndexedItem> terms = QueryTree.getPositiveTerms(x);
        assertEquals(4, terms.size());
        for (IndexedItem term : terms) {
            switch (term.getIndexedString()) {
            case "a":
            case "c":
                assertFalse(((Item) term).isRanked());
                break;
            case "b":
            case "d":
                assertTrue(((Item) term).isRanked());
                break;
            default:
                fail();
            }
        }
    }

    @Test
    public void testFieldAliases() {
        IndexInfoConfig modelConfig = new IndexInfoConfig(new IndexInfoConfig.Builder().indexinfo(new Indexinfo.Builder()
                .name("music").command(new Command.Builder().indexname("title").command("index"))
                .alias(new Alias.Builder().alias("song").indexname("title"))));
        IndexModel model = new IndexModel(modelConfig, (QrSearchersConfig)null);

        IndexFacts indexFacts = new IndexFacts(model);
        ParserEnvironment parserEnvironment = new ParserEnvironment().setIndexFacts(indexFacts);
        YqlParser configuredParser = new YqlParser(parserEnvironment);
        QueryTree x = configuredParser.parse(new Parsable()
                .setQuery("select * from sources * where title contains \"a\" and song contains \"b\";"));
        List<IndexedItem> terms = QueryTree.getPositiveTerms(x);
        assertEquals(2, terms.size());
        for (IndexedItem term : terms) {
            assertEquals("title", term.getIndexName());
        }
    }

    @Test
    public void testRegexp() {
        QueryTree x = parse("select * from sources * where foo matches \"a b\";");
        Item root = x.getRoot();
        assertSame(RegExpItem.class, root.getClass());
        assertEquals("a b", ((RegExpItem) root).stringValue());
    }

    @Test
    public void testWordAlternatives() {
        QueryTree x = parse("select * from sources * where foo contains alternatives({\"trees\": 1.0, \"tree\": 0.7});");
        Item root = x.getRoot();
        assertSame(WordAlternativesItem.class, root.getClass());
        WordAlternativesItem alternatives = (WordAlternativesItem) root;
        checkWordAlternativesContent(alternatives);
    }

    @Test
    public void testWordAlternativesWithOrigin() {
        QueryTree x = parse("select * from sources * where foo contains"
                + " ([{\"origin\": {\"original\": \" trees \", \"offset\": 1, \"length\": 5}}]"
                + "alternatives({\"trees\": 1.0, \"tree\": 0.7}));");
        Item root = x.getRoot();
        assertSame(WordAlternativesItem.class, root.getClass());
        WordAlternativesItem alternatives = (WordAlternativesItem) root;
        checkWordAlternativesContent(alternatives);
        Substring origin = alternatives.getOrigin();
        assertEquals(1, origin.start);
        assertEquals(6, origin.end);
        assertEquals("trees", origin.getValue());
        assertEquals(" trees ", origin.getSuperstring());
    }

    @Test
    public void testWordAlternativesInPhrase() {
        QueryTree x = parse("select * from sources * where"
                + " foo contains phrase(\"forest\", alternatives({\"trees\": 1.0, \"tree\": 0.7}));");
        Item root = x.getRoot();
        assertSame(PhraseItem.class, root.getClass());
        PhraseItem phrase = (PhraseItem) root;
        assertEquals(2, phrase.getItemCount());
        assertEquals("forest", ((WordItem) phrase.getItem(0)).getWord());
        checkWordAlternativesContent((WordAlternativesItem) phrase.getItem(1));
    }

    private void checkWordAlternativesContent(WordAlternativesItem alternatives) {
        boolean seenTree = false;
        boolean seenForest = false;
        final String forest = "trees";
        final String tree = "tree";
        assertEquals(2, alternatives.getAlternatives().size());
        for (WordAlternativesItem.Alternative alternative : alternatives.getAlternatives()) {
            if (tree.equals(alternative.word)) {
                assertFalse("Duplicate term introduced", seenTree);
                seenTree = true;
                assertEquals(.7d, alternative.exactness, 1e-15d);
            } else if (forest.equals(alternative.word)) {
                assertFalse("Duplicate term introduced", seenForest);
                seenForest = true;
                assertEquals(1.0d, alternative.exactness, 1e-15d);
            } else {
                fail("Unexpected term: " + alternative.word);
            }
        }
    }

    private void assertParse(String yqlQuery, String expectedQueryTree) {
        assertEquals(expectedQueryTree, parse(yqlQuery).toString());
    }

    private void assertParseFail(String yqlQuery, Throwable expectedException) {
        try {
            parse(yqlQuery);
        } catch (Throwable t) {
            assertEquals(expectedException.getClass(), t.getClass());
            assertEquals(expectedException.getMessage(), t.getMessage());
            return;
        }
        fail("Parse succeeded: " + yqlQuery);
    }

    private void assertSources(String yqlQuery, Collection<String> expectedSources) {
        parse(yqlQuery);
        assertEquals(new HashSet<>(expectedSources), parser.getYqlSources());
    }

    private void assertSummaryFields(String yqlQuery, Collection<String> expectedSummaryFields) {
        parse(yqlQuery);
        assertEquals(new HashSet<>(expectedSummaryFields), parser.getYqlSummaryFields());
    }

    private WordItem getRootWord(String yqlQuery) {
        Item root = parse(yqlQuery).getRoot();
        assertTrue(root instanceof WordItem);
        return (WordItem)root;
    }

    private void assertRootClass(String yqlQuery, Class<? extends Item> expectedRootClass) {
        assertEquals(expectedRootClass, parse(yqlQuery).getRoot().getClass());
    }

    private QueryTree parse(String yqlQuery) {
        return parser.parse(new Parsable().setQuery(yqlQuery));
    }

    private static String toString(List<VespaGroupingStep> steps) {
        List<String> actual = new ArrayList<>(steps.size());
        for (VespaGroupingStep step : steps) {
            actual.add(step.continuations().toString() +
                       step.getOperation());
        }
        return actual.toString();
    }
}
