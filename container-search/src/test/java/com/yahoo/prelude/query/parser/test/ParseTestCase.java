// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser.test;

import com.yahoo.language.Language;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.AndSegmentItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.SubstringItem;
import com.yahoo.prelude.query.SuffixItem;
import com.yahoo.prelude.query.TaggableItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.language.process.SpecialTokens;
import com.yahoo.prelude.query.parser.TestLinguistics;
import com.yahoo.search.Query;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests query parsing.
 *
 * @author bratseth
 */
public class ParseTestCase {

    private final ParsingTester tester = new ParsingTester();

    @Test
    void testSimpleTermQuery() {
        tester.assertParsed("foobar", "foobar", Query.Type.ANY);
    }

    @Test
    void testTermWithIndexPrefix() {
        tester.assertParsed("url:foobar",
                "url:foobar",
                Query.Type.ANY);
    }

    @Test
    void testTermWithCatalogAndIndexPrefix() {
        tester.assertParsed("normal.title:foobar", "normal.title:foobar", Query.Type.ANY);
    }

    @Test
    void testMultipleTermsWithUTF8EncodingOred() {
        tester.assertParsed("OR l\u00e5gen delta M\u00dcNICH M\u00fcnchen",
                "l\u00e5gen delta M\u00dcNICH M\u00fcnchen",
                Query.Type.ANY);
    }

    @Test
    void testMultipleTermsWithMultiplePrefixes() {
        tester.assertParsed("RANK (+bar -normal.title:foo -baz) url:foobar",
                "url:foobar +bar -normal.title:foo -baz", Query.Type.ANY);
    }

    @Test
    void testSimpleQueryDefaultOr() {
        tester.assertParsed("OR foobar foo bar baz", "foobar foo bar baz", Query.Type.ANY);
    }

    @Test
    void testOrAndNot() {
        tester.assertParsed("RANK (+(AND baz bar) -xyzzy -foobaz) foobar foo",
                "foobar +baz foo -xyzzy -foobaz +bar", Query.Type.ANY);
    }

    @Test
    void testSimpleOrNestedAnd() {
        tester.assertParsed("RANK (OR foo bar baz) foobar xyzzy",
                "foobar +(foo bar baz) xyzzy", Query.Type.ANY);
    }

    @Test
    void testSimpleOrNestedNot() {
        tester.assertParsed("+(OR foobar xyzzy) -(AND foo bar baz)",
                "foobar -(foo bar baz) xyzzy", Query.Type.ANY);
    }

    @Test
    void testOrNotNestedAnd() {
        tester.assertParsed("RANK (+(AND baz (OR foo bar baz) bar) -xyzzy -foobaz) foobar foo",
                "foobar +baz foo -xyzzy +(foo bar baz) -foobaz +bar",
                Query.Type.ANY);
    }

    @Test
    void testOrAndNotNestedNot() {
        tester.assertParsed("RANK (+(AND baz bar) -xyzzy -(AND foo bar baz) -foobaz) foobar foo",
                "foobar +baz foo -xyzzy -(foo bar baz) -foobaz +bar",
                Query.Type.ANY);
    }

    @Test
    void testOrMultipleNestedAnd() {
        tester.assertParsed("RANK (AND (OR fo ba foba) (OR foz baraz)) foobar foo bar baz",
                "foobar +(fo ba foba) foo bar +(foz baraz) baz",
                Query.Type.ANY);
    }

    @Test
    void testOrMultipleNestedNot() {
        tester.assertParsed("+(OR foobar foo bar baz) -(AND fo ba foba) -(AND foz baraz)",
                "foobar -(fo ba foba) foo bar -(foz baraz) baz",
                Query.Type.ANY);
    }

    @Test
    void testOrAndNotMultipleNestedAnd() {
        tester.assertParsed("RANK (+(AND baz (OR foo bar baz) (OR foz bazaz) bar) -xyzzy -foobaz) foobar foo",
                "foobar +baz foo -xyzzy +(foo bar baz) -foobaz +(foz bazaz) +bar",
                Query.Type.ANY);
    }

    @Test
    void testOrAndNotMultipleNestedNot() {
        tester.assertParsed("RANK (+(AND baz bar) -xyzzy -(AND foo bar baz) -foobaz -(AND foz bazaz)) foobar foo",
                "foobar +baz foo -xyzzy -(foo bar baz) -foobaz -(foz bazaz) +bar",
                Query.Type.ANY);
    }

    @Test
    void testOrMultipleNestedAndNot() {
        tester.assertParsed("RANK (+(AND (OR ffoooo bbaarr) (OR oof rab raboof)) -(AND fo ba foba) -(AND foz baraz)) foobar foo bar baz",
                "foobar -(fo ba foba) foo +(ffoooo bbaarr) bar +(oof rab raboof) -(foz baraz) baz",
                Query.Type.ANY);
    }

    @Test
    void testOrAndNotMultipleNestedAndNot() {
        tester.assertParsed("RANK (+(AND (OR ffoooo bbaarr) (OR oof rab raboof) baz xyxyzzy) -(AND fo ba foba) -foo -bar -(AND foz baraz)) foobar",
                "foobar -(fo ba foba) -foo +(ffoooo bbaarr) -bar +(oof rab raboof) -(foz baraz) +baz +xyxyzzy",
                Query.Type.ANY);
    }

    @Test
    void testExplicitPhrase() {
        Item root = tester.assertParsed("\"foo bar foobar\"", "\"foo bar foobar\"", Query.Type.ANY);
        assertTrue(root instanceof PhraseItem);
        assertTrue(((PhraseItem) root).isExplicit());
    }

    @Test
    void testPhraseWithIndex() {
        tester.assertParsed("normal.title:\"foo bar foobar\"",
                "normal.title:\"foo bar foobar\"", Query.Type.ANY);
    }

    @Test
    void testPhrasesAndTerms() {
        tester.assertParsed("OR \"foo bar foobar\" xyzzy \"baz gaz faz\"",
                "\"foo bar foobar\" xyzzy \"baz gaz faz\"", Query.Type.ANY);
    }

    @Test
    void testPhrasesAndTermsWithOperators() {
        tester.assertParsed("RANK (+(AND \"baz gaz faz\" bazar) -\"foo bar foobar\") foofoo xyzzy",
                "foofoo -\"foo bar foobar\" xyzzy +\"baz gaz faz\" +bazar",
                Query.Type.ANY);
    }

    @Test
    void testSimpleTermQueryDefaultAnd() {
        tester.assertParsed("foobar", "foobar", Query.Type.ALL);
    }

    @Test
    void testTermWithCatalogAndIndexPrefixDefaultAnd() {
        tester.assertParsed("normal.title:foobar", "normal.title:foobar", Query.Type.ALL);
    }

    @Test
    void testMultipleTermsWithMultiplePrefixesDefaultAnd() {
        tester.assertParsed("+(AND url:foobar bar) -normal.title:foo -baz",
                "url:foobar +bar -normal.title:foo -baz",
                Query.Type.ALL);
    }

    @Test
    void testSimpleQueryDefaultAnd() {
        tester.assertParsed("AND foobar foo bar baz", "foobar foo bar baz", Query.Type.ALL);
    }

    @Test
    void testNotDefaultAnd() {
        tester.assertParsed("+(AND foobar (OR foo bar baz) xyzzy) -(AND foz baraz bazar)",
                "foobar +(foo bar baz) xyzzy -(foz baraz bazar)",
                Query.Type.ALL);
    }

    @Test
    void testSimpleTermQueryDefaultPhrase() {
        tester.assertParsed("foobar",
                "foobar",
                Query.Type.PHRASE);
    }

    @Test
    void testSimpleQueryDefaultPhrase() {
        Item root = tester.assertParsed("\"foobar foo bar baz\"",
                "foobar foo bar baz",
                Query.Type.PHRASE);
        assertTrue(root instanceof PhraseItem);
        assertFalse(((PhraseItem) root).isExplicit());
    }

    @Test
    void testMultipleTermsWithMultiplePrefixesDefaultPhrase() {
        tester.assertParsed("\"url foobar bar normal title foo baz\"",
                "url:foobar +bar -normal.title:foo -baz",
                Query.Type.PHRASE);
    }

    @Test
    void testOdd1() {
        tester.assertParsed("AND window print error", "+window.print() +error",
                Query.Type.ALL);
    }

    @Test
    void testOdd2() {
        tester.assertParsed("normal.title:kaboom", "normal.title:\"kaboom\"",
                Query.Type.ALL);
    }

    @Test
    void testOdd2Uppercase() {
        tester.assertParsed("normal.title:KABOOM", "NORMAL.TITLE:\"KABOOM\"", Query.Type.ALL);
    }

    @Test
    void testOdd3() {
        tester.assertParsed("AND foo (OR size.all:[200;300] date.all:512)",
                "foo +(size.all:[200;300] date.all:512)", Query.Type.ALL);
    }

    @Test
    void testNullQuery() {
        tester.assertParsed(null, null, Query.Type.ALL);
    }

    @Test
    void testEmptyQuery() {
        tester.assertParsed(null, "", Query.Type.ALL);
    }

    @Test
    void testNotOnly() {
        tester.assertParsed(null, "-foobar", Query.Type.ALL);
    }

    @Test
    void testMultipleNotsOnlt() {
        tester.assertParsed(null, "-foo -bar -foobar", Query.Type.ALL);
    }

    @Test
    void testOnlyNotComposite() {
        tester.assertParsed(null, "-(foo bar baz)", Query.Type.ALL);
    }

    @Test
    void testNestedCompositesDefaultOr() {
        tester.assertParsed("RANK (OR foobar bar baz) foo xyzzy",
                "foo +(foobar +(bar baz)) xyzzy", Query.Type.ANY);
    }

    @Test
    void testNestedCompositesDefaultAnd() {
        tester.assertParsed("AND foo (OR foobar bar baz) xyzzy",
                "foo +(foobar +(bar baz)) xyzzy", Query.Type.ALL);
    }

    @Test
    void testNestedCompositesPhraseDefault() {
        tester.assertParsed("\"foo foobar bar baz xyzzy\"",
                "foo +(foobar +(bar baz)) xyzzy", Query.Type.PHRASE);
    }

    @Test
    void testNumeric() {
        tester.assertParsed("34", "34", Query.Type.ANY);
    }

    @Test
    void testGreaterNumeric() {
        tester.assertParsed("<454", "<454", Query.Type.ANY);
    }

    @Test
    void testGreaterNumericWithIndex() {
        IntItem item = (IntItem) tester.assertParsed("score:<454", "score:<454", Query.Type.ANY);
        assertEquals("score", item.getIndexName());
        assertEquals(454L, item.getToLimit().number());
        assertFalse(item.getToLimit().isInclusive());
    }

    @Test
    void testSmallerNumeric() {
        tester.assertParsed(">454", ">454", Query.Type.ANY);
    }

    @Test
    void testSmallerNumericWithIndex() {
        IntItem item = (IntItem) tester.assertParsed("score:>454", "score:>454", Query.Type.ANY);
        assertEquals("score", item.getIndexName());
        assertEquals(454L, item.getFromLimit().number());
        assertFalse(item.getFromLimit().isInclusive());
    }

    @Test
    void testFullRange() {
        tester.assertParsed("[34;454]", "[34;454]", Query.Type.ANY);
    }

    @Test
    void testFullRangeLimit() {
        tester.assertParsed("[34;454;7]", "[34;454;7]", Query.Type.ANY);
        tester.assertParsed("[34;454;-7]", "[34;454;-7]", Query.Type.ANY);
    }

    @Test
    void testLowOpenRange() {
        tester.assertParsed("[;454]", "[;454]", Query.Type.ANY);
    }

    @Test
    void testHiOpenRange() {
        tester.assertParsed("[34;]", "[34;]", Query.Type.ANY);
    }

    @Test
    void testNumericWithIndex() {
        tester.assertParsed("document.size:[34;454]", "document.size:[34;454]", Query.Type.ANY);
    }

    @Test
    void testMultipleNumeric() {
        tester.assertParsed("OR [34;454] <34", "[34;454] <34", Query.Type.ANY);
    }

    @Test
    void testMultipleIntegerWithIndex() {
        tester.assertParsed("OR document.size:[34;454] date:>1234567890",
                "document.size:[34;454] date:>1234567890", Query.Type.ANY);
    }

    @Test
    void testMixedNumericAndOtherTerms() {
        tester.assertParsed("RANK (AND document.size:<1024 xyzzy) foo date:>123456890",
                "foo +document.size:<1024 +xyzzy date:>123456890",
                Query.Type.ANY);
    }

    @Test
    void testEmptyPhrase() {
        tester.assertParsed("\"to be or not\"", "\"\"to be or not", Query.Type.ANY);
    }

    @Test
    void testItemPhraseEmptyPhrase() {
        tester.assertParsed("RANK to \"or not to be\"", "+to\"or not to be\"\"\"", Query.Type.ANY);
    }

    @Test
    void testSimpleQuery() {
        tester.assertParsed("OR if am \"f g 4 2\" maybe", "if am \"  f g 4 2\"\" maybe", Query.Type.ANY);
    }

    @Test
    void testExcessivePluses() {
        tester.assertParsed("+(AND other is nothing) -test",
                "++other +++++is ++++++nothing -test", Query.Type.ANY);
    }

    @Test
    void testMinusAndPluses() {
        tester.assertParsed(null, "--test+-if", Query.Type.ANY);
    }

    @Test
    void testPlusesAndMinuses() {
        tester.assertParsed("AND a b c d d", "a+b+c+d--d", Query.Type.ANY);
    }

    @Test
    void testNumbers() {
        tester.assertParsed("AND 123 2132odfd 934032 32423", "123+2132odfd.934032,,32423", Query.Type.ANY);
    }

    @Test
    void testOtherSignsInQuote() {
        tester.assertParsed("AND 0032 4 320 24329043", "0032+4\\320.24329043", Query.Type.ANY);
    }

    @Test
    void testGribberish() {
        tester.assertParsed("1349832840234l3040roer\u00e6lf12",
                ",1349832840234l3040roer\u00e6lf12",
                Query.Type.ANY);
    }

    @Test
    void testUrl() {
        tester.assertParsed("AND www:www www:hotelaiguablava www:com",
                "+www:www.hotelaiguablava:com",
                Query.Type.ANY);
    }

    @Test
    void testUrlGribberish() {
        tester.assertParsed("OR (AND 3 16) fast.type:lycosoffensive",
                "[ 3:16 fast.type:lycosoffensive",
                Query.Type.ANY);
    }

    @Test
    void testBracedWordAny() {
        tester.assertParsed("foo", "(foo)", Query.Type.ANY);
    }

    @Test
    void testBracedWordAll() {
        tester.assertParsed("foo", "(foo)", Query.Type.ALL);

    }

    @Test
    void testBracedWords() {
        tester.assertParsed("OR (OR foo bar) (OR xyzzy foobar)",
                "(foo bar) (xyzzy foobar)", Query.Type.ANY);
    }

    @Test
    void testNullAdvanced() {
        tester.assertParsed(null, null, Query.Type.ADVANCED);
    }

    @Test
    void testEmptyAdvanced() {
        tester.assertParsed(null, "", Query.Type.ADVANCED);
    }

    @Test
    void testSimpleAdvanced() {
        tester.assertParsed("foobar", "foobar", Query.Type.ADVANCED);
    }

    @Test
    void testPrefixAdvanced() {
        tester.assertParsed("url:foobar", "url:foobar", Query.Type.ADVANCED);
    }

    @Test
    void testPrefixWithDotAdvanced() {
        tester.assertParsed("normal.title:foobar", "normal.title:foobar", Query.Type.ADVANCED);
    }

    @Test
    void testUTF8Advanced() {
        tester.assertParsed("m\u00fcnchen", "m\u00fcnchen", Query.Type.ADVANCED);
    }

    @Test
    void testSimplePhraseAdvanced() {
        tester.assertParsed("\"foo bar foobar\"", "\"foo bar foobar\"", Query.Type.ADVANCED);
    }

    @Test
    void testSimplePhraseWithIndexAdvanced() {
        tester.assertParsed("normal.title:\"foo bar foobar\"",
                "normal.title:\"foo bar foobar\"",
                Query.Type.ADVANCED);
    }

    @Test
    void testMultiplePhrasesAdvanced() {
        tester.assertParsed("AND \"foo bar foobar\" \"baz gaz faz\"",
                "\"foo bar foobar\" and \"baz gaz faz\"",
                Query.Type.ADVANCED);
    }

    @Test
    void testNumberAdvanced() {
        tester.assertParsed("34", "34", Query.Type.ADVANCED);
    }

    @Test
    void testLargerNumberAdvanced() {
        tester.assertParsed("<454", "<454", Query.Type.ADVANCED);
    }

    @Test
    void testLesserNumberAdvanced() {
        tester.assertParsed(">454", ">454", Query.Type.ADVANCED);
    }

    @Test
    void testRangeAdvanced() {
        tester.assertParsed("[34;454]", "[34;454]", Query.Type.ADVANCED);
    }

    @Test
    void testLowOpenRangeAdvanced() {
        tester.assertParsed("[;454]", "[;454]", Query.Type.ADVANCED);
    }

    @Test
    void testHighOpenRangeAdvanced() {
        tester.assertParsed("[34;]", "[34;]", Query.Type.ADVANCED);
    }

    @Test
    void testIdexedRangeAdvanced() {
        tester.assertParsed("document.size:[34;454]", "document.size:[34;454]", Query.Type.ADVANCED);
    }

    @Test
    void testSimpleAndAdvanced() {
        tester.assertParsed("AND foo bar", "foo and bar", Query.Type.ADVANCED);
    }

    @Test
    void testSimpleOrAdvanced() {
        tester.assertParsed("OR foo bar", "foo or bar", Query.Type.ADVANCED);
    }

    @Test
    void testSimpleAndNotAdvanced() {
        tester.assertParsed("+foo -bar", "foo andnot bar", Query.Type.ADVANCED);
    }

    @Test
    void testSimpleRankAdvanced() {
        tester.assertParsed("RANK foo bar", "foo rank bar", Query.Type.ADVANCED);
    }

    @Test
    void testMultipleAndAdvanced() {
        tester.assertParsed("AND foo bar foobar", "foo and bar and foobar", Query.Type.ADVANCED);
    }

    @Test
    void testMultipleOrAdvanced() {
        tester.assertParsed("OR foo bar foobar", "foo or bar or foobar", Query.Type.ADVANCED);
    }

    @Test
    void testMultipleAndnotAdvanced() {
        tester.assertParsed("+foo -bar -foobar", "foo andnot bar andnot foobar", Query.Type.ADVANCED);
    }

    @Test
    void testMultipleRankAdvanced() {
        tester.assertParsed("RANK foo bar foobar", "foo rank bar rank foobar", Query.Type.ADVANCED);
    }

    @Test
    void testMixedAdvanced() {
        tester.assertParsed("OR (AND foo bar) foobar", "foo and bar or foobar", Query.Type.ADVANCED);
    }

    @Test
    void testNestedAdvanced() {
        tester.assertParsed("AND foo (OR bar foobar)", "foo and (bar or foobar)", Query.Type.ADVANCED);
    }

    @Test
    void testMultipleNestedAdvanced() {
        tester.assertParsed("+(AND foo xyzzy) -(OR bar foobar)",
                "(foo and xyzzy) andnot (bar or foobar)", Query.Type.ADVANCED);
    }

    @Test
    void testDoubleNestedAdvanced() {
        tester.assertParsed("AND foo (OR bar (OR xyzzy foobar))",
                "foo and (bar or (xyzzy or foobar))", Query.Type.ADVANCED);
    }

    @Test
    void testDeeplyAdvanced() {
        tester.assertParsed(
                "AND foo (OR bar (OR (AND (AND baz (+(OR bazar zyxxy) -fozbaz)) (OR boz bozor) xyzzy) foobar))",
                "foo and (bar or ((baz and ((bazar or zyxxy) andnot fozbaz)) and (boz or bozor) and xyzzy or foobar))",
                Query.Type.ADVANCED);
    }

    @Test
    void testDeeplyAdvancedUppercase() {
        tester.assertParsed(
                "AND FOO (OR BAR (OR (AND (AND BAZ (+(OR BAZAR ZYXXY) -FOZBAZ)) (OR BOZ BOZOR) XYZZY) FOOBAR))",
                "FOO AND (BAR OR ((BAZ AND ((BAZAR OR ZYXXY) ANDNOT FOZBAZ)) AND (BOZ OR BOZOR) AND XYZZY OR FOOBAR))",
                Query.Type.ADVANCED);
    }

    @Test
    void testAbortedIntegerRange() {
        tester.assertParsed("AND audio.audall:744 audio.audall:ph",
                "+audio.audall:[744 +audio.audall:ph", Query.Type.ANY);
    }

    @Test
    void testJunk() {
        tester.assertParsed("+l -fast.type:offensive",
                ",;'/.<l:>? -fast.type:offensive", Query.Type.ALL);
    }

    @Test
    void testOneTermPhraseWithIndex() {
        tester.assertParsed("normal.title:foo", "normal.title:\"foo\"", Query.Type.ANY);
    }

    @Test
    void testOneTermPhraseWithIndexAdvanced() {
        tester.assertParsed("normal.title:foo", "normal.title:\"foo\"", Query.Type.ADVANCED);
    }

    @Test
    void testIncorrect1Advanced() {
        tester.assertParsed("\"to be or not\"", "\"\"to be or not", Query.Type.ADVANCED);
    }

    @Test
    void testIncorrect2Advanced() {
        tester.assertParsed("AND to \"or not to be\"", "+to\"or not to be\"\"\"", Query.Type.ADVANCED);
    }

    @Test
    void testIncorrect3Advanced() {
        tester.assertParsed("AND if am \"f g 4 2\" maybe",
                "if am \"  f g 4 2\"\" maybe", Query.Type.ADVANCED);
    }

    @Test
    void testIncorrect4Advanced() {
        tester.assertParsed("AND other is nothing test",
                "++other +++++is ++++++nothing -test", Query.Type.ADVANCED);
    }

    @Test
    void testImplicitPhrase1Advanced() {
        tester.assertParsed("AND test if", "--test+-if", Query.Type.ADVANCED);
    }

    @Test
    void testImplicitPhrase2Advanced() {
        tester.assertParsed("AND a b c d d", "a+b+c+d--d", Query.Type.ADVANCED);
    }

    @Test
    void testImplicitPhrase3Advanced() {
        tester.assertParsed("AND 123 2132odfd 934032 32423",
                "123+2132odfd.934032,,32423", Query.Type.ADVANCED);
    }

    @Test
    void testImplicitPhrase4Advanced() {
        tester.assertParsed("AND 0032 4 320 24329043", "0032+4\\320.24329043", Query.Type.ADVANCED);
    }

    @Test
    void testUtf8Advanced() {
        tester.assertParsed("1349832840234l3040roer\u00e6lf12",
                ",1349832840234l3040roer\u00e6lf12", Query.Type.ADVANCED);
    }

    @Test
    void testOperatorSearchAdvanced() {
        tester.assertParsed("RANK (OR (AND and and) or andnot) rank",
                "and and and or or or andnot rank rank", Query.Type.ADVANCED);
    }

    @Test
    void testIncorrectParenthesisAdvanced() {
        tester.assertParsed("AND foo bar", "foo and bar )", Query.Type.ADVANCED);
    }

    @Test
    void testOpeningParenthesisOnlyAdvanced() {
        tester.assertParsed("AND foo (OR bar (AND foobar xyzzy))",
                "(foo and (bar or (foobar and xyzzy", Query.Type.ADVANCED);
    }

    @Test
    void testSimpleWeight() {
        tester.assertParsed("foo!150", "foo!", Query.Type.ANY);
    }

    @Test
    void testMultipleWeight() {
        tester.assertParsed("foo!250", "foo!!!", Query.Type.ANY);
    }

    @Test
    void testExplicitWeight() {
        tester.assertParsed("foo!200", "foo!200", Query.Type.ANY);
    }

    @Test
    void testExplicitZeroWeight() {
        tester.assertParsed("foo!0", "foo!0", Query.Type.ANY);
    }

    @Test
    void testSimplePhraseWeight() {
        tester.assertParsed("\"foo bar\"!150", "\"foo bar\"!", Query.Type.ANY);
    }

    @Test
    void testSingleHyphen() {
        tester.assertParsed("AND a b", "a-b", Query.Type.ALL);
    }

    @Test
    void testUserCase() {
        tester.assertParsed("\"a a\"", "\"a- a-*\"", Query.Type.ALL);
    }

    @Test
    void testMultiplePhraseWeight() {
        tester.assertParsed("\"foo bar\"!250", "\"foo bar\"!!!", Query.Type.ANY);
    }

    @Test
    void testExplicitPhraseWeight() {
        tester.assertParsed("\"foo bar\"!200", "\"foo bar\"!200", Query.Type.ANY);
    }

    @Test
    void testUrlSubmodeHyphen() {
        assertTrue(ParsingTester.createIndexFacts().newSession(new Query()).getIndex("url.all").isUriIndex());
        tester.assertParsed("url.all:\"www-microsoft com\"", "url.all:www-microsoft.com", Query.Type.ANY);
    }

    @Test
    void testUrlSubmodeUnderscore() {
        tester.assertParsed("url.all:\"www_microsoft com\"", "url.all:www_microsoft.com", Query.Type.ANY);
    }

    @Test
    void testUrlSubmode() {
        tester.assertParsed("host.all:\"www-microsoft com $\"", "host.all:www-microsoft.com", Query.Type.ANY);
    }

    @Test
    void testExplicitHostNameAnchoringHost() {
        tester.assertParsed("host.all:\"^ www-microsoft com $\"", "host.all:^www-microsoft.com$", Query.Type.ANY);
    }

    @Test
    void testExplicitHostNameAnchoringSite() {
        tester.assertParsed("site:\"^ www-microsoft com $\"", "site:^www-microsoft.com$", Query.Type.ANY);
    }

    @Test
    void testExplicitHostNameAnchoring() {
        tester.assertParsed("host.all:\"^ http www krangaz-central com index html $\"",
                "host.all:^http://www.krangaz-central.com/index.html$",
                Query.Type.ANY);
    }

    @Test
    void testExplicitHostAnchoringRemoval() {
        tester.assertParsed("host.all:\"www-microsoft com\"",
                "host.all:www-microsoft.com*", Query.Type.ANY);

    }

    @Test
    void testQuery1Any() {
        tester.assertParsed("RANK (AND fast \"search engine\") kernel",
                "+fast +\"search engine\" kernel", Query.Type.ANY);

    }

    @Test
    void testQuery1Advanced() {
        tester.assertParsed("RANK (AND fast \"search engine\") kernel",
                "+fast and \"search engine\" rank kernel", Query.Type.ADVANCED);

    }

    @Test
    void testQuery2Any() {
        tester.assertParsed("+(OR title:car bmw) -z3", "title:car bmw -z3",
                Query.Type.ANY);

    }

    @Test
    void testQuery2Advanced() {
        tester.assertParsed("+(OR title:car bmw) -z3", "title:car or bmw andnot z3", Query.Type.ADVANCED);
    }

    @Test
    void testQuery3All() {
        tester.assertParsed("+(AND FAST search domain:no pagedepth:0) -title:phrase",
                "FAST search -title:phrase domain:no Pagedepth:0",
                Query.Type.ALL);
    }

    @Test
    void testQuery4Advanced() {
        tester.assertParsed("AND (+(AND FAST search) -title:phrase) domain:no pagedepth:0",
                "FAST and search andnot title:phrase and domain:no and Pagedepth:0",
                Query.Type.ADVANCED);
    }

    @Test
    void testQuery5Any() {
        tester.assertParsed("AND alltheweb fast search", "+alltheweb +fast +search", Query.Type.ANY);
    }

    @Test
    void testQuery6Any() {
        tester.assertParsed("RANK (+(AND query language) -sql) search", "+query +language -sql search", Query.Type.ANY);
    }

    @Test
    void testQuery7Any() {
        tester.assertParsed(
                "+(AND alltheweb (OR search engine)) -(OR excite altavista)",
                "(alltheweb and (search or engine)) andnot (excite or altavista)",
                Query.Type.ADVANCED);
    }

    @Test
    void testQuery8Advanced() {
        tester.assertParsed(
                "RANK (AND \"search engines\" \"query processing\") \"fast search\"",
                "(\"search engines\" and \"query processing\") rank \"fast search\"",
                Query.Type.ADVANCED);
    }

    @Test
    void testPStrangeAdvanced() {
        tester.assertParsed("AND AND r.s:jnl", "( AND +r.s:jnl) ", Query.Type.ADVANCED);
    }

    @Test
    void testEmptyNestAdvanced() {
        tester.assertParsed(null, "() ", Query.Type.ADVANCED);
    }

    @Test
    void testNestedBeginningAdvanced() {
        tester.assertParsed("AND (OR a b) c", "(a or b) and c", Query.Type.ADVANCED);
    }

    @Test
    void testNestedPositiveAny() {
        tester.assertParsed("AND (OR a b) c", "+(a b) +c", Query.Type.ANY);
    }

    @Test
    void testParseAdvancedQuery() {
        tester.assertParsed("AND joplin remediation r.s:jnl",
                "(joplin and + and remediation and +r.s:jnl)",
                Query.Type.ADVANCED);
    }

    @Test
    void testSimpleDotPhraseAny() {
        tester.assertParsed("OR a (AND b c) d", "a b.c d", Query.Type.ANY);
    }

    @Test
    void testSimpleHyphenPhraseAny() {
        tester.assertParsed("OR a (AND b c) d", "a b-c d", Query.Type.ANY);
    }

    @Test
    void testAnotherSimpleDotPhraseAny() {
        tester.assertParsed("OR (AND a b) c d", "a.b c d", Query.Type.ANY);
    }

    @Test
    void testYetAnotherSimpleDotPhraseAny() {
        tester.assertParsed("OR a b (AND c d)", "a b c.d", Query.Type.ANY);
    }

    @Test
    void testVariousSeparatorsPhraseAny() {
        tester.assertParsed("AND a b c d", "a-b.c%d", Query.Type.ANY);
    }

    @Test
    void testDoublyMarkedPhraseAny() {
        tester.assertParsed("OR a \"b c\" d", "a \"b.c\" d", Query.Type.ANY);
    }

    @Test
    void testPartlyDoublyMarkedPhraseAny() {
        tester.assertParsed("OR a \"b c d\"", "a \"b.c d\"", Query.Type.ANY);
    }

    @Test
    void testIndexedDottedPhraseAny() {
        tester.assertParsed("OR a (AND url:b url:c) d", "a url:b.c d", Query.Type.ANY);
    }

    @Test
    void testIndexedPlusedPhraseAny() {
        tester.assertParsed("OR a (AND normal.title:b normal.title:c) d", "a normal.title:b+c d", Query.Type.ANY);
    }

    @Test
    void testNestedNotAny() {
        tester.assertParsed(
                "RANK (+(OR normal.title:foobar (AND url:www url:pvv url:org)) -foo) a",
                "a +(normal.title:foobar url:www.pvv.org) -foo", Query.Type.ANY);
    }

    @Test
    void testDottedPhraseAdvanced() {
        tester.assertParsed("OR a (AND b c)", "a or b.c", Query.Type.ADVANCED);
    }

    @Test
    void testHyphenPhraseAdvanced() {
        tester.assertParsed("OR (AND a (AND b c)) d", "a and b-c or d", Query.Type.ADVANCED);
    }

    @Test
    void testAnotherDottedPhraseAdvanced() {
        tester.assertParsed("OR (AND a b) c", "a.b or c", Query.Type.ADVANCED);
    }

    @Test
    void testNottedDottedPhraseAdvanced() {
        tester.assertParsed("+a -(AND c d)", "a andnot c.d", Query.Type.ADVANCED);
    }

    @Test
    void testVariousSeparatorsPhraseAdvanced() {
        tester.assertParsed("AND a b c d", "a-b.c%d", Query.Type.ADVANCED);
    }

    @Test
    void testDoublyPhrasedAdvanced() {
        tester.assertParsed("OR (AND a \"b c\") d", "a and \"b.c\" or d", Query.Type.ADVANCED);
    }

    @Test
    void testPartlyDoublyPhrasedAdvanced() {
        tester.assertParsed("OR a \"b c d\"", "a or \"b.c d\"", Query.Type.ADVANCED);
    }

    @Test
    void testNestedDottedPhraseAdvanced() {
        tester.assertParsed("AND a (OR url:\"b c\" d)", "a and(url:\"b.c\" or d)", Query.Type.ADVANCED);
    }

    @Test
    void testNestedPlussedPhraseAdvanced() {
        tester.assertParsed("AND (OR a (AND normal.title:b normal.title:c)) d",
                "a or normal.title:b+c and d", Query.Type.ADVANCED);
    }

    @Test
    void testNottedNestedDottedPhraseAdvanced() {
        tester.assertParsed(
                "+(AND a (OR normal.title:foobar (AND url:www url:pvv url:org))) -foo",
                "a and (normal.title:foobar or url:www.pvv.org) andnot foo",
                Query.Type.ADVANCED);
    }

    @Test
    void testPlusedThenQuotedPhraseAny() {
        tester.assertParsed("\"a b c\"", "a+\"b c\"", Query.Type.ANY);
    }

    @Test
    void testPlusedTwiceThenQuotedPhraseAny() {
        tester.assertParsed("AND a b c d", "a+b+\"c d\"", Query.Type.ANY);
    }

    @Test
    void testPlusedThenQuotedPhraseAdvanced() {
        tester.assertParsed("\"a b c\"", "a+\"b c\"", Query.Type.ADVANCED);
    }

    @Test
    void testPhrasesInBraces() {
        tester.assertParsed("AND url.domain:microsoft url.domain:com",
                "+(url.domain:microsoft.com)", Query.Type.ALL);
    }

    @Test
    void testDoublyPhrasedPhrasesInBraces() {
        tester.assertParsed("url.domain:\"microsoft com\"",
                "+(url.domain:\"microsoft.com\")", Query.Type.ALL);
    }

    @Test
    void testSinglePrefixTerm() {
        Item root = tester.assertParsed("prefix*", "prefix*", Query.Type.ANY);
        assertTrue(root instanceof PrefixItem);
    }

    @Test
    void testSingleSubstringTerm() {
        Item root = tester.assertParsed("*substring*", "*substring*", Query.Type.ANY);
        assertTrue(root instanceof SubstringItem);
    }

    @Test
    void testSingleSuffixTerm() {
        Item root = tester.assertParsed("*suffix", "*suffix", Query.Type.ANY);
        assertTrue(root instanceof SuffixItem);
    }

    @Test
    void testPrefixAndWordTerms() {
        Item root = tester.assertParsed("OR foo prefix* bar", "foo prefix* bar", Query.Type.ANY);
        assertTrue(((OrItem) root).getItem(1) instanceof PrefixItem);
    }

    @Test
    void testSubstringAndWordTerms() {
        Item root = tester.assertParsed("OR foo *substring* bar", "foo *substring* bar", Query.Type.ANY);
        assertTrue(((OrItem) root).getItem(1) instanceof SubstringItem);
    }

    @Test
    void testSuffixAndWordTerms() {
        Item root = tester.assertParsed("OR foo *suffix bar", "foo *suffix bar", Query.Type.ANY);
        assertTrue(((OrItem) root).getItem(1) instanceof SuffixItem);
    }

    @Test
    void testPhraseNotPrefix() {
        tester.assertParsed("OR foo (AND prefix bar)", "foo prefix*bar", Query.Type.ANY);
    }

    @Test
    void testPhraseNotSubstring() {
        tester.assertParsed("OR foo (AND substring bar)", "foo *substring*bar", Query.Type.ANY);
    }

    @Test
    void testPhraseNotSuffix() {
        tester.assertParsed("OR (AND foo suffix) bar", "foo*suffix bar", Query.Type.ANY);
    }

    @Test
    void testIndexedPrefix() {
        Item root = tester.assertParsed("foo.bar:prefix*", "foo.bar:prefix*", Query.Type.ANY);
        assertTrue(root instanceof PrefixItem);
    }

    @Test
    void testIndexedSubstring() {
        Item root = tester.assertParsed("foo.bar:*substring*", "foo.bar:*substring*", Query.Type.ANY);
        assertTrue(root instanceof SubstringItem);
    }

    @Test
    void testIndexedSuffix() {
        Item root = tester.assertParsed("foo.bar:*suffix", "foo.bar:*suffix", Query.Type.ANY);
        assertTrue(root instanceof SuffixItem);
    }

    @Test
    void testIndexedPhraseNotPrefix() {
        tester.assertParsed("AND foo.bar:prefix foo.bar:xyzzy", "foo.bar:prefix*xyzzy", Query.Type.ANY);
    }

    @Test
    void testIndexedPhraseNotSubstring() {
        tester.assertParsed("AND foo.bar:substring foo.bar:xyzzy", "foo.bar:*substring*xyzzy", Query.Type.ANY);
    }

    @Test
    void testIndexedPhraseNotSuffix() {
        tester.assertParsed("AND foo.bar:xyzzy foo.bar:suffix", "foo.bar:xyzzy*suffix", Query.Type.ANY);
    }

    @Test
    void testPrefixWithWeight() {
        Item root = tester.assertParsed("prefix*!200", "prefix*!200", Query.Type.ANY);
        assertTrue(root instanceof PrefixItem);
    }

    @Test
    void testSubstringWithWeight() {
        Item root = tester.assertParsed("*substring*!200", "*substring*!200", Query.Type.ANY);
        assertTrue(root instanceof SubstringItem);
    }

    @Test
    void testSuffixWithWeight() {
        Item root = tester.assertParsed("*suffix!200", "*suffix!200", Query.Type.ANY);
        assertTrue(root instanceof SuffixItem);
    }

    /** Non existing index → and **/
    @Test
    void testNonIndexPhraseNotPrefix() {
        tester.assertParsed("AND void prefix", "void:prefix*", Query.Type.ANY);
    }

    @Test
    void testNonIndexPhraseNotSubstring() {
        tester.assertParsed("AND void substring", "void:*substring*", Query.Type.ANY);
    }

    @Test
    void testNonIndexPhraseNotSuffix() {
        tester.assertParsed("AND void suffix", "void:*suffix", Query.Type.ANY);
    }

    /** Explicit phrase → remove '*' **/
    @Test
    void testExplicitPhraseNotPrefix() {
        tester.assertParsed("\"prefix bar\"", "\"prefix* bar\"", Query.Type.ANY);
    }

    @Test
    void testExplicitPhraseNotSubstring() {
        tester.assertParsed("\"substring bar\"", "\"*substring* bar\"", Query.Type.ANY);
    }

    @Test
    void testExplicitPhraseNotSuffix() {
        tester.assertParsed("\"suffix bar\"", "\"*suffix bar\"", Query.Type.ANY);
    }

    /** Extra star is ignored */
    @Test
    void testPrefixExtraStar() {
        Item root = tester.assertParsed("prefix*", "prefix**", Query.Type.ANY);
        assertTrue(root instanceof PrefixItem);
    }

    @Test
    void testSubstringExtraStar() {
        Item root = tester.assertParsed("*substring*", "**substring**", Query.Type.ANY);
        assertTrue(root instanceof SubstringItem);
    }

    @Test
    void testSuffixExtraStar() {
        Item root = tester.assertParsed("*suffix", "**suffix", Query.Type.ANY);
        assertTrue(root instanceof SuffixItem);
    }

    @Test
    void testPrefixExtraSpace() {
        Item root = tester.assertParsed("prefix", "prefix *", Query.Type.ANY);
        assertTrue(root instanceof WordItem);
    }

    @Test
    void testSubstringExtraSpace() {
        Item root = tester.assertParsed("*substring*", "* substring*", Query.Type.ANY);
        assertTrue(root instanceof SubstringItem);
    }

    @Test
    void testSubstringExtraSpace2() {
        Item root = tester.assertParsed("*substring", "* substring *", Query.Type.ANY);
        assertTrue(root instanceof SuffixItem);
    }

    @Test
    void testSuffixExtraSpace() {
        Item root = tester.assertParsed("*suffix", "* suffix", Query.Type.ANY);
        assertTrue(root instanceof SuffixItem);
    }

    /** Extra spaces with index **/
    @Test
    void testIndexPrefixExtraSpace() {
        tester.assertParsed("AND foo prefix", "foo:prefix *", Query.Type.ANY);
    }

    @Test
    void testIndexSubstringExtraSpace() {
        Item root = tester.assertParsed("OR foo substring*", "foo:* substring*", Query.Type.ANY);
        assertTrue(((OrItem) root).getItem(0) instanceof WordItem);
        assertTrue(((OrItem) root).getItem(1) instanceof PrefixItem);
    }

    @Test
    void testIndexSubstringExtraSpace2() {
        Item root = tester.assertParsed("OR foo substring", "foo:* substring *", Query.Type.ANY);
        assertTrue(((OrItem) root).getItem(0) instanceof WordItem);
        assertTrue(((OrItem) root).getItem(1) instanceof WordItem);
    }

    @Test
    void testIndexSuffixExtraSpace() {
        Item root = tester.assertParsed("OR foo suffix", "foo:* suffix", Query.Type.ANY);
        assertTrue(((OrItem) root).getItem(0) instanceof WordItem);
        assertTrue(((OrItem) root).getItem(1) instanceof WordItem);
    }

    /** Various tests for prefix, substring, and suffix terms **/
    @Test
    void testTermsWithStarsAndSpaces() {
        tester.assertParsed("OR foo *bar", "foo * bar", Query.Type.ANY);
    }

    @Test
    void testTermsWithStarsAndSpaces2() {
        tester.assertParsed("OR foo *bar *baz", "foo * * bar * * baz", Query.Type.ANY);
    }

    @Test
    void testTermsWithStarsAndPlussAndMinus() {
        tester.assertParsed("+(AND *bar baz*) -*foo*", "+*bar -*foo* +baz*", Query.Type.ANY);
    }

    @Test
    void testTermsWithStarsAndPlussAndMinus2() {
        tester.assertParsed("OR *bar *foo baz", "+ * bar - * foo * + baz *", Query.Type.ANY);
    }

    @Test
    void testTermsWithStarsAndExclamation() {
        tester.assertParsed("OR foo* 200 *bar* 200 *baz 200", "foo* !200 *bar* !200 *baz !200", Query.Type.ANY);
    }

    @Test
    void testTermsWithStarsAndExclamation2() {
        tester.assertParsed("OR foo 200 *bar 200", "foo *!200 *bar *!200", Query.Type.ANY);
    }

    @Test
    void testTermsWithStarsAndParenthesis() {
        tester.assertParsed("RANK *baz *bar* foo*", "(foo*) (*bar*) (*baz)", Query.Type.ANY);
    }

    @Test
    void testTermsWithStarsAndParenthesis2() {
        tester.assertParsed("RANK baz bar foo", "(foo)* *(bar)* *(baz)", Query.Type.ANY);
    }


    @Test
    void testSimpleAndFilter() {
        tester.assertParsed("AND bar |foo", "bar", "+foo", Query.Type.ANY);
    }

    @Test
    void testSimpleRankFilter() {
        tester.assertParsed("RANK bar |foo", "bar", "foo", Query.Type.ANY);
    }

    @Test
    void testSimpleNotFilter() {
        tester.assertParsed("+bar -|foo", "bar", "-foo", Query.Type.ANY);
    }

    @Test
    void testSimpleCompoundFilter1() {
        tester.assertParsed("RANK (AND bar |foo1) |foo2", "bar", "+foo1 foo2",
                Query.Type.ANY);
    }

    @Test
    void testSimpleCompoundFilter2() {
        tester.assertParsed("+(AND bar |foo1) -|foo3", "bar", "+foo1 -foo3",
                Query.Type.ANY);
    }

    @Test
    void testSimpleCompoundFilter3() {
        tester.assertParsed("RANK (+bar -|foo3) |foo2", "bar", "foo2 -foo3",
                Query.Type.ANY);
    }

    @Test
    void testSimpleCompoundFilter4() {
        tester.assertParsed("RANK (+(AND bar |foo1) -|foo3) |foo2", "bar",
                "+foo1 foo2 -foo3", Query.Type.ANY);
    }

    @Test
    void testAndFilterEmptyQuery() {
        tester.assertParsed("|foo", "", "+foo", Query.Type.ANY);
    }

    @Test
    void testRankFilterEmptyQuery() {
        tester.assertParsed("|foo", "", "foo", Query.Type.ANY);
    }

    @Test
    void testNotFilterEmptyQuery() {
        tester.assertParsed(null, "", "-foo", Query.Type.ANY);
    }

    @Test
    void testCompoundFilter1EmptyQuery() {
        tester.assertParsed("RANK |foo1 |foo2", "", "+foo1 foo2", Query.Type.ANY);
    }

    @Test
    void testCompoundFilter2EmptyQuery() {
        tester.assertParsed("+|foo1 -|foo3", "", "+foo1 -foo3", Query.Type.ANY);
    }

    @Test
    void testCompoundFilter3EmptyQuery() {
        tester.assertParsed("+|foo2 -|foo3", "", "foo2 -foo3", Query.Type.ANY);
    }

    @Test
    void testCompoundFilter4EmptyQuery() {
        tester.assertParsed("RANK (+|foo1 -|foo3) |foo2", "", "+foo1 foo2 -foo3",
                Query.Type.ANY);
    }

    @Test
    void testMultitermAndFilter() {
        tester.assertParsed("AND bar |foo |foz", "bar", "+foo +foz", Query.Type.ANY);
    }

    @Test
    void testMultitermRankFilter() {
        tester.assertParsed("RANK bar |foo |foz", "bar", "foo foz", Query.Type.ANY);
    }

    @Test
    void testMultitermNotFilter() {
        tester.assertParsed("+bar -|foo -|foz", "bar", "-foo -foz", Query.Type.ANY);
    }

    @Test
    void testMultitermCompoundFilter1() {
        tester.assertParsed("RANK (AND bar |foo1 |foz1) |foo2 |foz2", "bar",
                "+foo1 +foz1 foo2 foz2", Query.Type.ANY);
    }

    @Test
    void testMultitermCompoundFilter2() {
        tester.assertParsed("+(AND bar |foo1 |foz1) -|foo3 -|foz3", "bar",
                "+foo1 +foz1 -foo3 -foz3", Query.Type.ANY);
    }

    @Test
    void testMultitermCompoundFilter3() {
        tester.assertParsed("RANK (+bar -|foo3 -|foz3) |foo2 |foz2", "bar",
                "foo2 foz2 -foo3 -foz3", Query.Type.ANY);
    }

    @Test
    void testMultitermCompoundFilter4() {
        tester.assertParsed("RANK (+(AND bar |foo1 |foz1) -|foo3 -|foz3) |foo2 |foz2",
                "bar", "+foo1 +foz1 foo2 foz2 -foo3 -foz3", Query.Type.ANY);
    }

    @Test
    void testMultitermAndFilterEmptyQuery() {
        tester.assertParsed("AND |foo |foz", "", "+foo +foz", Query.Type.ANY);
    }

    @Test
    void testMultitermRankFilterEmptyQuery() {
        tester.assertParsed("OR |foo |foz", "", "foo foz", Query.Type.ANY);
    }

    @Test
    void testMultitermNotFilterEmptyQuery() {
        tester.assertParsed(null, "", "-foo -foz", Query.Type.ANY);
    }

    @Test
    void testMultitermCompoundFilter1EmptyQuery() {
        tester.assertParsed("RANK (AND |foo1 |foz1) |foo2 |foz2", "",
                "+foo1 +foz1 foo2 foz2", Query.Type.ANY);
    }

    @Test
    void testMultitermCompoundFilter2EmptyQuery() {
        tester.assertParsed("+(AND |foo1 |foz1) -|foo3 -|foz3", "",
                "+foo1 +foz1 -foo3 -foz3", Query.Type.ANY);
    }

    @Test
    void testMultitermCompoundFilter3EmptyQuery() {
        tester.assertParsed("+(OR |foo2 |foz2) -|foo3 -|foz3", "",
                "foo2 foz2 -foo3 -foz3", Query.Type.ANY);
    }

    @Test
    void testMultitermCompoundFilter4EmptyQuery() {
        tester.assertParsed("RANK (+(AND |foo1 |foz1) -|foo3 -|foz3) |foo2 |foz2", "",
                "+foo1 +foz1 foo2 foz2 -foo3 -foz3", Query.Type.ANY);
    }

    @Test
    void testMultipleDifferentPhraseSeparators() {
        tester.assertParsed("AND foo bar", "foo.-.bar", Query.Type.ANY);
    }

    @Test
    void testNoisyFilter() {
        tester.assertParsed("RANK (+(AND foobar kanoo) -|foo) |bar", "foobar and kanoo",
                "-foo ;+;bar", Query.Type.ADVANCED);
    }

    @Test
    void testReallyNoisyQuery1() {
        tester.assertParsed("AND word another", "&word\"()/&#)(/&another!\"", Query.Type.ALL);
    }

    @Test
    void testReallyNoisyQuery2() {
        tester.assertParsed("AND \u03bc\u03bc hei", "&&&`\u00b5\u00b5=@hei", Query.Type.ALL);
    }

    @Test
    void testReallyNoisyQuery3() {
        tester.assertParsed("AND hei hallo du der", "hei-hallo;du;der", Query.Type.ALL);
    }

    @Test
    void testNumberParsing() {
        Item root = tester.parseQuery("normal:400", null, Language.UNKNOWN, Query.Type.ANY, TestLinguistics.INSTANCE);
        assertEquals(root.getCode(), 5);
    }

    @Test
    void testRangeParsing() {
        Item root = tester.parseQuery("normal:[5;400]", null, Language.UNKNOWN, Query.Type.ANY, TestLinguistics.INSTANCE);
        assertEquals(root.toString(), "normal:[5;400]");
        assertEquals(root.getCode(), 5);
    }

    @Test
    void testNumberAsPrefix() {
        Item root = tester.assertParsed("89*", "89*", Query.Type.ANY);
        assertTrue(root instanceof PrefixItem);
    }

    @Test
    void testNumberAsSubstring() {
        Item root = tester.assertParsed("*89*", "*89*", Query.Type.ANY);
        assertTrue(root instanceof SubstringItem);
    }

    @Test
    void testNumberAsSuffix() {
        Item root = tester.assertParsed("*89", "*89", Query.Type.ANY);
        assertTrue(root instanceof SuffixItem);
    }

    @Test
    void testTheStupidSymbolsWhichAreNowWordCharactersInUnicode() {
        tester.assertParsed("AND yz a", "yz\u00A8\u00AA\u00AF", Query.Type.ANY);
    }

    @Test
    void testTWoWords() {
        tester.assertParsed("\"hei h\u00e5\"", "\"hei h\u00e5\"", Query.Type.ANY);
    }

    @Test
    void testLoneStar() {
        assertTrue(tester.parseQuery("*", null, Language.UNKNOWN, Query.Type.ANY, TestLinguistics.INSTANCE) instanceof NullItem);
    }

    @Test
    void testLoneStarWithFilter() {
        tester.assertParsed("|a", "*", "+a", Query.Type.ANY);
    }

    @Test
    void testImplicitPhrasingWithIndex() {
        tester.assertParsed("AND a:b a:c", "a:/b/c", Query.Type.ANY);
    }

    @Test
    void testSingleNoisyTermWithIndex() {
        tester.assertParsed("a:b", "a:/b", Query.Type.ANY);
    }

    @Test
    void testSingleNoisyPhraseWithIndex() {
        tester.assertParsed("AND mail:yahoo mail:com", "mail:@yahoo.com", Query.Type.ANY);
    }

    @Test
    void testPhraseWithWeightAndIndex() {
        tester.assertParsed("to:\"a b\"!150", "to:\"a b\"!150", Query.Type.ANY);
    }

    @Test
    void testTermWithWeightAndIndex() {
        tester.assertParsed("to:a!150", "to:a!150", Query.Type.ANY);
    }

    @Test
    void testPhrasingWithIndexAndQuerySyntax() {
        tester.assertParsed("to:\"a b c\"", "to:\"a (b c)\"", Query.Type.ANY);
    }

    @Test
    void testPhrasingWithIndexAndHalfBrokenQuerySyntax() {
        tester.assertParsed("to:\"a b c\"", "to:\"a +b c)\"", Query.Type.ANY);
    }

    @Test
    void testURLHostQueryOneTerm1() {
        tester.assertParsed("site:\"com $\"", "site:com", Query.Type.ANY);
    }

    @Test
    void testURLHostQueryOneTerm2() {
        tester.assertParsed("site:com", "site:com*", Query.Type.ANY);
    }

    @Test
    void testURLHostQueryOneTerm3() {
        tester.assertParsed("site:\"com $\"", "site:.com", Query.Type.ANY);
    }

    @Test
    void testURLHostQueryOneTerm4() {
        tester.assertParsed("site:\"^ com $\"", "site:^com", Query.Type.ANY);
    }

    @Test
    void testURLHostQueryOneTerm5() {
        tester.assertParsed("site:\"^ com\"", "site:^com*", Query.Type.ANY);
    }

    @Test
    void testFullURLQuery() {
        tester.assertParsed(
                "url.all:\"http shopping yahoo-inc com 1080 this is a path shop d hab id 1804905709 frag1\"",
                "url.all:http://shopping.yahoo-inc.com:1080/this/is/a/path/shop?d=hab&id=1804905709#frag1",
                Query.Type.ANY);
    }

    @Test
    void testURLQueryHyphen() {
        tester.assertParsed(
                "url.all:\"http news bbc co uk go rss test - sport1 hi tennis 4112866 stm\"",
                "url.all:http://news.bbc.co.uk/go/rss/test/-/sport1/hi/tennis/4112866.stm",
                Query.Type.ANY);
    }

    @Test
    void testURLQueryUnderScoreNumber() {
        tester.assertParsed(
                "url.all:\"ap 20050621 45_ap_on_re_la_am_ca aruba_missing_teen_5\"",
                "url.all:/ap/20050621/45_ap_on_re_la_am_ca/aruba_missing_teen_5",
                Query.Type.ANY);
    }

    @Test
    void testOtherComplexUrls() {
        tester.assertParsed(
                "url.all:\"http redir folha com br redir online dinheiro rss091 http www1 folha uol com br folha dinheiro ult91u96593 shtml\"",
                "url.all:http://redir.folha.com.br/redir/online/dinheiro/rss091/*http://www1.folha.uol.com.br/folha/dinheiro/ult91u96593.shtml",
                Query.Type.ALL);
        tester.assertParsed(
                "url.all:\"http economista com mx online4 nsf all 6FC11CB53F8A305B0625702700709029 OpenDocument\"",
                "url.all:http://economista.com.mx/online4.nsf/(all)/6FC11CB53F8A305B0625702700709029?OpenDocument",
                Query.Type.ALL);
        tester.assertParsed(
                "url.all:\"http www tierradelfuego info index php s AR13xbyxg espectaculos programa ARc7hzxb\"",
                "url.all:http://www.tierradelfuego.info/index.php?s=AR13xbyxg$$espectaculos/programa$ARc7hzxb",
                Query.Type.ALL);
        tester.assertParsed(
                "url.all:\"http www newsadvance com servlet Satellite pagename LNA MGArticle IMD_BasicArticle c MGArticle cid 1031782787014 path mgnetwork diversions\"",
                "url.all:http://www.newsadvance.com/servlet/Satellite?pagename=LNA/MGArticle/IMD_BasicArticle&c=MGArticle&cid=1031782787014&path=!mgnetwork!diversions",
                Query.Type.ALL);
        tester.assertParsed(
                "AND ull:http ull:www ull:neue ull:oz ull:de ull:information ull:pub ull:Boulevard ull:index ull:html ull:file ull:a ull:3 ull:s ull:4 ull:file s:\"37 iptc bdt 20050607 294 dpa 9001170 txt\" s:\"3 dir\" s:\"26 opt DPA parsed boulevard\" s:\"7 bereich\" s:\"9 Boulevard\"",
                "ull:http://www.neue-oz.de/information/pub_Boulevard/index.html?file=a:3:{s:4:\"file\";s:37:\"iptc-bdt-20050607-294-dpa_9001170.txt\";s:3:\"dir\";s:26:\"/opt/DPA/parsed/boulevard/\";s:7:\"bereich\";s:9:\"Boulevard\";}",
                Query.Type.ALL);
    }

    @Test
    void testTooGreedyUrlParsing() {
        tester.assertParsed("AND site:\"nypost com $\" about", "site:nypost.com about",
                Query.Type.ALL);
    }

    @Test
    void testTooGreedyUrlParsing2() {
        tester.assertParsed("AND site:\"nypost com $\" about foo",
                "site:nypost.com about foo", Query.Type.ALL);
    }

    @Test
    void testSimplerDurbin() {
        tester.assertParsed("+(OR language:en \"Durbin said\" a) -newstype:rssexclude",
                "( a (\"Durbin said\" ) -newstype:rssexclude (language:en )",
                Query.Type.ALL);
    }

    @Test
    void testSimplerDurbin2() {
        tester.assertParsed("+(AND \"Durbin said\" language:en) -newstype:rssexclude",
                "( , (\"Durbin said\" ) -newstype:rssexclude (language:en )",
                Query.Type.ALL);
    }

    @Test
    void testDurbin() {
        tester.assertParsed(
                "AND \"Richard Durbin\" Welfare (+(OR language:en \"Durbin said\" \"Durbin says\" \"Durbin added\" \"Durbin agreed\" \"Durbin questioned\" date:>1109664000) -newstype:rssexclude)",
                "(\"Richard Durbin\" ) \"Welfare\" ((\"Durbin said\" \"Durbin says\" \"Durbin added\" \"Durbin agreed\" \"Durbin questioned\" )  -newstype:rssexclude date:>1109664000 (language:en )",
                Query.Type.ALL);
    }

    @Test
    void testTooLongQueryTerms() {
        tester.assertParsed("AND 545558598787gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggcfffffffffffffffffffffffffffffffffffffffffffccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccclllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyytttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrreeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeewwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqlkjhcxxdfffxdzzaqwwsxedcrfvtgbyhnujmikkiloolpppof filter ew 545558598787gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggcfffffffffffffffffffffffffffffffffffffffffffccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccclllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyytttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrreeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeewwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqlkjhcxxdfffxdzzaqwwsxedcrfvtgbyhnujmikkiloolpppof 2b 2f 545558598787gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggcfffffffffffffffffffffffffffffffffffffffffffccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccclllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyytttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrreeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeewwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqlkjhcxxdfffxdzzaqwwsxedcrfvtgbyhnujmikkiloolpppof",
                "+/545558598787gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggcfffffffffffffffffffffffffffffffffffffffffffccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccclllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyytttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrreeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeewwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqlkjhcxxdfffxdzzaqwwsxedcrfvtgbyhnujmikkiloolpppof&filter=ew:545558598787gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggcfffffffffffffffffffffffffffffffffffffffffffccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccclllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyytttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrreeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeewwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqlkjhcxxdfffxdzzaqwwsxedcrfvtgbyhnujmikkiloolpppof!1000 =.2b..2f.545558598787gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggcfffffffffffffffffffffffffffffffffffffffffffccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccclllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyytttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrreeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeewwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqlkjhcxxdfffxdzzaqwwsxedcrfvtgbyhnujmikkiloolpppof",
                Query.Type.ALL);
    }

    @Test
    void testNonSpecialTokenParsing() {
        ParsingTester customTester = new ParsingTester(SpecialTokens.empty());
        customTester.assertParsed("OR c or c with (AND tcp ip)", "c# or c++ with tcp/ip", Query.Type.ANY);
    }

    @Test
    void testNonIndexWithColons1() {
        tester.assertParsed("OR this is (AND notan iindex)", "this is notan:iindex", Query.Type.ANY);
    }

    @Test
    void testNonIndexWithColons2() {
        tester.assertParsed("OR this is (AND notan iindex either)", "this is notan:iindex:either", Query.Type.ANY);
    }

    @Test
    void testIndexThenUnderscoreTermBecomesIndex() {
        tester.assertParsed("AND name:batch name:article", "name:batch_article", Query.Type.ANY);
    }

    @Test
    void testFakeCJKSegmenting() {
        // "first" "second" and "third" are segments in the test language
        Item item = tester.parseQuery("name:firstsecondthird", null, Language.CHINESE_SIMPLIFIED, Query.Type.ANY, TestLinguistics.INSTANCE);

        assertTrue(item instanceof AndSegmentItem);
        AndSegmentItem segment = (AndSegmentItem) item;

        assertEquals(3, segment.getItemCount());
        assertEquals("name:first", segment.getItem(0).toString());
        assertEquals("name:second", segment.getItem(1).toString());
        assertEquals("name:third", segment.getItem(2).toString());

        assertEquals("name", ((WordItem) segment.getItem(0)).getIndexName());
        assertEquals("name", ((WordItem) segment.getItem(1)).getIndexName());
        assertEquals("name", ((WordItem) segment.getItem(2)).getIndexName());
    }

    @Test
    void testFakeCJKSegmentingOfPhrase() {
        // "first" "second" and "third" are segments in the test language
        Item item = tester.parseQuery("name:\"firstsecondthird\"", null, Language.CHINESE_SIMPLIFIED, Query.Type.ANY, TestLinguistics.INSTANCE);

        assertTrue(item instanceof PhraseSegmentItem);
        PhraseSegmentItem segment = (PhraseSegmentItem) item;

        assertEquals(3, segment.getItemCount());
        assertEquals("name:first", segment.getItem(0).toString());
        assertEquals("name:second", segment.getItem(1).toString());
        assertEquals("name:third", segment.getItem(2).toString());

        assertEquals("name", ((WordItem) segment.getItem(0)).getIndexName());
        assertEquals("name", ((WordItem) segment.getItem(1)).getIndexName());
        assertEquals("name", ((WordItem) segment.getItem(2)).getIndexName());
    }

    @Test
    void testAndItemAndImplicitPhrase() {
        tester.assertParsed("AND \u00d8 \u00d8 \u00d8 \u00d9",
                "\u00d8\u00b9\u00d8\u00b1\u00d8\u00a8\u00d9", "",
                Query.Type.ALL, Language.CHINESE_SIMPLIFIED);
    }

    @Test
    void testAvoidMultiLevelAndForLongCJKQueries() {
        Item root = tester.parseQuery(
                "\u30d7\u30ed\u91ce\u7403\u962a\u795e\u306e\u672c\u62e0\u5730\u3001\u7532\u5b50\u5712\u7403\u5834\uff08\u5175\u5eab\u770c\u897f\u5bae\u5e02\uff09\u306f\uff11\u65e5\u3001\uff11\uff19\uff12\uff14\u5e74\u30d7\u30ed\u91ce\u7403\u962a\u795e\u306e\u672c\u62e0\u5730\u3001\u7532\u5b50\u5712\u7403\u5834\uff08\u5175\u5eab\u770c\u897f\u5bae\u5e02\uff09\u306f\uff11\u65e5\u3001\uff11\uff19\uff12\uff14\u5e74\u30d7\u30ed\u91ce\u7403\u962a\u795e\u306e\u672c\u62e0\u5730\u3001\u7532\u5b50\u5712\u7403\u5834\uff08\u5175\u5eab\u770c\u897f\u5bae\u5e02\uff09\u306f\uff11\u65e5\u3001\uff11\uff19\uff12\uff14\u5e74\u30d7\u30ed\u91ce\u7403\u962a\u795e\u306e\u672c\u62e0\u5730\u3001\u7532\u5b50\u5712\u7403\u5834\uff08\u5175\u5eab\u770c\u897f\u5bae\u5e02\uff09\u306f\uff11\u65e5\u3001\uff11\uff19\uff12\uff14\u5e74\u30d7\u30ed\u91ce\u7403\u962a\u795e\u306e\u672c\u62e0\u5730\u3001\u7532\u5b50\u5712\u7403\u5834\uff08\u5175\u5eab\u770c\u897f\u5bae\u5e02\uff09\u306f\uff11\u65e5\u3001\uff11\uff19\uff12\uff14\u5e74\u30d7\u30ed\u91ce\u7403\u962a\u795e\u306e\u672c\u62e0\u5730\u3001\u7532\u5b50\u5712\u7403\u5834\uff08\u5175\u5eab\u770c\u897f\u5bae\u5e02\uff09\u306f\uff11\u65e5\u3001\uff11\uff19\uff12\uff14\u5e74",
                "", Language.UNKNOWN, Query.Type.ALL, TestLinguistics.INSTANCE);

        assertTrue(4 > stackDepth(0, root), "Query tree too deep when parsing CJK queries.");
    }

    private int stackDepth(int i, Item root) {
        if (root instanceof CompositeItem) {
            int maxDepth = i;

            for (Iterator<Item> j = ((CompositeItem) root).getItemIterator(); j.hasNext();) {
                int newDepth = stackDepth(i + 1, j.next());

                maxDepth = java.lang.Math.max(maxDepth, newDepth);
            }
            return maxDepth;
        } else {
            return i;
        }
    }

    @Test
    void testFakeCJKSegmentingOfMultiplePhrases() {
        Item item = tester.parseQuery("name:firstsecond.s", null, Language.CHINESE_SIMPLIFIED, Query.Type.ANY, TestLinguistics.INSTANCE);
        assertEquals("AND (SAND name:first name:second) name:s", item.toString());
    }

    @Test
    void testOrFilter() {
        tester.assertParsed("AND d (OR |a |b)", "d", "+(a b)", Query.Type.ALL);
    }

    @Test
    void testOrFilterWithTypeAdv() {
        tester.assertParsed("AND d (OR |a |b)", "d", "+(a b)", Query.Type.ADVANCED);
    }

    @Test
    void testPhraseFilter() {
        tester.assertParsed("AND d |\"a b\"", "d", "+\"a b\"", Query.Type.ALL);
    }

    @Test
    void testMinusAndFilter() {
        tester.assertParsed("+d -(AND |a |b)", "d", "-(a b)", Query.Type.ALL);
    }

    @Test
    void testOrAndSomeTermsFilter() {
        tester.assertParsed("RANK d |c |a |b |e", "d", "c (a b) e", Query.Type.ALL);
    }

    // This is an ugly parse tree, but it's at least reasonable
    @Test
    void testOrAndSomeTermsFilterAndAnAnd() {
        AndItem root = (AndItem) tester.assertParsed("AND (RANK d |c |a |b |e) (OR |e |f)", "d", "c (a b) e +(e f)", Query.Type.ALL);
        assertFalse(root.isFilter()); // AND
        assertFalse(root.getItem(0).isFilter()); // RANK
        assertFalse(((RankItem) root.getItem(0)).getItem(0).isFilter()); // d
        assertTrue(((RankItem) root.getItem(0)).getItem(1).isFilter()); // c
        assertTrue(((RankItem) root.getItem(0)).getItem(2).isFilter()); // a
        assertTrue(((RankItem) root.getItem(0)).getItem(3).isFilter()); // b
        assertTrue(((RankItem) root.getItem(0)).getItem(4).isFilter()); // e
        assertFalse(root.getItem(1).isFilter()); // OR
        assertTrue(((OrItem) root.getItem(1)).getItem(0).isFilter()); // e
        assertTrue(((OrItem) root.getItem(1)).getItem(1).isFilter()); // f
    }

    @Test
    void testUrlNotConsumingBrace() {
        tester.assertParsed("AND A (OR url.all:B url.all:C) D E", "A (url.all:B url.all:C) D E", Query.Type.ALL);
    }

    // Really a syntax error on part of the user, but it's part of
    // the logic where balanced braces are allowed in URLs
    @Test
    void testUrlNotConsumingBrace2() {
        tester.assertParsed("AND A (OR url.all:B url.all:C) D E",
                "A (url.all:B url.all:C)D E", Query.Type.ALL);
    }

    @Test
    void testSiteNotConsumingBrace() {
        tester.assertParsed("AND A (OR site:\"B $\" site:\"C $\") D E",
                "A (site:B site:C) D E", Query.Type.ALL);
    }

    @Test
    void testCommaOnlyLeadsToImplicitPhrasing() {
        tester.assertParsed("AND A B C", "A,B,C", Query.Type.ALL);
    }

    @Test
    void testBangDoesNotBindAcrossSpace() {
        tester.assertParsed("word", "word !", Query.Type.ALL);
    }

    @Test
    void testLotsOfPlusMinus() {
        tester.assertParsed("OR word term", "word - + - + - term", Query.Type.ANY);
    }

    @Test
    void testLotsOfMinusPlus() {
        tester.assertParsed("OR word term", "word - + - + term", Query.Type.ANY);
    }

    @Test
    void testMinusDoesNotBindAcrossSpace() {
        tester.assertParsed("OR word term", "word - term", Query.Type.ANY);
    }

    @Test
    void testPlusDoesNotBindAcrossSpace() {
        tester.assertParsed("OR word term", "word + term", Query.Type.ANY);
    }

    @Test
    void testMinusDoesNotBindAcrossSpaceAllQuery() {
        tester.assertParsed("AND word term", "word - term", Query.Type.ALL);
    }

    @Test
    void testPlusDoesNotBindAcrossSpaceAllQuery() {
        tester.assertParsed("AND word term", "word + term", Query.Type.ALL);
    }

    @Test
    void testNoSpaceInIndexPrefix() {
        tester.assertParsed("AND url domain:s url.domain:b",
                "url. domain:s url.domain:b", Query.Type.ALL);
    }

    @Test
    void testNestedParensAndLittleElse() {
        tester.assertParsed("OR a b c d", "((a b) (c d))", Query.Type.ALL);
    }

    // This is simply to control it doesn't crash
    @Test
    void testNestedParensAndLittleElseMoreBroken() {
        tester.assertParsed("AND (OR a b) (OR c d)", "(a b) +(c d))", Query.Type.ALL);
    }

    @Test
    void testNestedUnbalancedParensAndLittleElseMoreBroken() {
        tester.assertParsed("OR a b c d", "((a b) +(c d)", Query.Type.ALL);
    }

    @Test
    void testUnbalancedParens() {
        tester.assertParsed("AND a b (OR c d)", "a b) +(c d))", Query.Type.ALL);
    }

    @Test
    void testUnbalancedStartingParens() {
        tester.assertParsed("OR a b c d", "((a b) +(c d", Query.Type.ALL);
    }

    @Test
    void testJPMobileExceptionQuery() {
        tester.assertParsed("OR concat and (AND make string) 1 47 or",
                "(concat \"and\" (make-string 1 47) \"or\")", Query.Type.ALL);
    }

    @Test
    void testColonDoesNotBindAcrossSpace() {
        tester.assertParsed("b", "a: b", Query.Type.ALL);
        tester.assertParsed("AND a b", "a : b", Query.Type.ALL);
        tester.assertParsed("AND a b", "a :b", Query.Type.ALL);
        tester.assertParsed("AND a b", "a.:b", Query.Type.ALL);
        tester.assertParsed("a:b", "a:b", Query.Type.ALL);
    }

    @Test
    void testGermanUriDecompounding() {
        tester.assertParsed("url.all:\"kommunikationsfehler de\"",
                "url.all:kommunikationsfehler.de", "", Query.Type.ALL, Language.GERMAN);
    }

    // Check the parser doesn't fail on these horrible query strings
    @Test
    void testTicket443882() {
        tester.assertParsed(
                "AND australian LOTTERY (+(OR language:en IN AFFILIATION WITH THE UK NATIONAL LOTTERY date:>1125475200) -newstype:rssexclude)",
                "australian LOTTERY (IN AFFILIATION WITH THE UK NATIONAL LOTTERY -newstype:rssexclude date:>1125475200 (language:en )",
                Query.Type.ALL);
        tester.assertParsed(
                "AND AND consulting (+(OR language:en albuquerque \"new mexico\" date:>1125475200) -newstype:rssexclude)",
                ") AND (consulting) ((albuquerque \"new mexico\" ) -newstype:rssexclude date:>1125475200 (language:en )",
                Query.Type.ALL);
        tester.assertParsed(
                "AND the church of Jesus Christ of latter Day Saints (+(OR language:en Mormon temples date:>1125475200) -newstype:rssexclude)",
                "the church of Jesus Christ of latter Day Saints (Mormon temples -newstype:rssexclude date:>1125475200 (language:en )",
                Query.Type.ALL);
    }

    @Test
    void testParensInQuotes() {
        tester.assertParsed("AND ringtone (OR a:\"Delivery SMAF large max 150kB 063\" a:\"RealMusic Delivery\")",
                "ringtone AND (a:\"Delivery SMAF large max.150kB (063)\" OR a:\"RealMusic Delivery\" )",
                Query.Type.ADVANCED);
        tester.assertParsed("AND ringtone AND (OR a:\"Delivery SMAF large max 150kB 063\" OR a:\"RealMusic Delivery\")",
                "ringtone AND (a:\"Delivery SMAF large max.150kB (063)\" OR a:\"RealMusic Delivery\" )",
                Query.Type.ALL);
        // The last one here is a little weird, but it's not a problem, so let it pass for now...
        tester.assertParsed("OR (OR ringtone AND) (OR a:\"Delivery SMAF large max 150kB 063\" OR a:\"RealMusic Delivery\")",
                "ringtone AND (a:\"Delivery SMAF large max.150kB (063)\" OR a:\"RealMusic Delivery\" )",
                Query.Type.ANY);
    }

    @Test
    void testMixedCaseIndexNames() {
        tester.assertParsed("AND mixedCase:a mixedCase:b notAnIndex c mixedCase:d",
                "mixedcase:a MIXEDCASE:b notAnIndex:c mixedCase:d",
                Query.Type.ALL);
    }

    /** CJK special tokens should be recognized also on non-boundaries */
    @Test
    void testChineseSpecialTokens() {
        tester.assertParsed("AND cat tcp/ip zu foo dotnet bar dotnet dotnet c# c++ bar dotnet dotnet wiz",
                "cattcp/ipzu foo.netbar.net.netC#c++bar.net.netwiz", "", Query.Type.ALL, Language.CHINESE_SIMPLIFIED);
    }

    /**
     * If a cjk special token replace is multi-segment, that token should perhaps be segmented
     * but right now it is not
     */
    @Test
    void testChineseSpecialTokensWithMultiSegmentReplace() {
        // special-token-fs is a special token, to be replaced by firstsecond, first and second are segments in test
        tester.assertParsed("AND tcp/ip firstsecond dotnet firstsecond (SAND first second)", "tcp/ipspecial-token-fs.net special-token-fs firstsecond",
                "", Query.Type.ALL, Language.CHINESE_SIMPLIFIED, TestLinguistics.INSTANCE);
    }

    @Test
    void testSpaceAndTermWeights() {
        tester.assertParsed("AND yahoo!360 yahoo 360 yahoo!150 360 yahoo 360 yahoo yahoo!150 yahoo!200",
                "yahoo!360 yahoo !360 yahoo! 360 yahoo ! 360 yahoo !!! yahoo! ! yahoo!!", Query.Type.ALL);
    }

    @Test
    void testNumbersAndNot() {
        tester.assertParsed("AND a -12", "a -12", Query.Type.ALL);
    }

    @Test
    void testNumbersAndDoubleNot() {
        tester.assertParsed("+a --12", "a --12", Query.Type.ALL);
    }

    @Test
    void testNegativeNumberWithIndex() {
        tester.assertParsed("normal:-12", "normal:-12", Query.Type.ALL);
    }

    @Test
    void testNegativeTermPositiveNumberWithIndex() {
        tester.assertParsed("+a -normal:12", "a -normal:12", Query.Type.ALL);
    }

    @Test
    void testNegativeTermNegativeNumberWithIndex() {
        tester.assertParsed("+a -normal:-12", "a -normal:-12", Query.Type.ALL);
    }

    @Test
    void testNegativeTermPositiveNumberInParentheses() {
        tester.assertParsed("+a -12", "a -(12)", Query.Type.ALL);
        tester.assertParsed("+a -(AND 12 15)", "a -(12 15)", Query.Type.ALL);
        tester.assertParsed("+a -12 -15", "a -(12) -(15)", Query.Type.ALL);
    }

    @Test
    void testSingleNegativeNumberLikeTerm() {
        tester.assertParsed("-12", "-12", Query.Type.ALL);
    }

    @Test
    void testNegativeLessThan() {
        tester.assertParsed("normal:<-3", "normal:<-3", Query.Type.ALL);
        tester.assertParsed("<-3", "<-3", Query.Type.ALL);
    }

    @Test
    void testNegativeBiggerThan() {
        tester.assertParsed("normal:>-3", "normal:>-3", Query.Type.ALL);
        tester.assertParsed(">-3", ">-3", Query.Type.ALL);
    }

    @Test
    void testNegativesInRanges() {
        tester.assertParsed("normal:[-4;9]", "normal:[-4;9]", Query.Type.ALL);
        tester.assertParsed("[-4;9]", "[-4;9]", Query.Type.ALL);
        tester.assertParsed("normal:[-4;-9]", "normal:[-4;-9]", Query.Type.ALL);
        tester.assertParsed("[-4;-9]", "[-4;-9]", Query.Type.ALL);
    }

    @Test
    void testDecimal() {
        Item root = tester.assertParsed("2.2", "2.2", Query.Type.ALL);
        assertTrue(root instanceof IntItem);
        tester.assertParsed("normal:2.2", "normal:2.2", Query.Type.ALL);
    }

    @Test
    void testVersionNumbers() {
        tester.assertParsed("AND 1 0 9", "1.0.9", Query.Type.ALL);
    }

    @Test
    void testDecimalNumbersAndNot() {
        tester.assertParsed("AND a -12.2", "a -12.2", Query.Type.ALL);
    }

    @Test
    void testDecimalNumbersAndDoubleNot() {
        tester.assertParsed("+a --12.2", "a --12.2", Query.Type.ALL);
    }

    @Test
    void testNegativeDecimalNumberWithIndex() {
        tester.assertParsed("normal:-12.2", "normal:-12.2", Query.Type.ALL);
    }

    @Test
    void testSingleNegativeDecimalNumberLikeTerm() {
        tester.assertParsed("-12.2", "-12.2", Query.Type.ALL);
    }

    @Test
    void testNegativeDecimalLessThan() {
        tester.assertParsed("normal:<-3.14", "normal:<-3.14", Query.Type.ALL);
        tester.assertParsed("<-3.14", "<-3.14", Query.Type.ALL);
    }

    @Test
    void testNegativeDecimalBiggerThan() {
        tester.assertParsed("normal:>-3.14", "normal:>-3.14", Query.Type.ALL);
        tester.assertParsed(">-3.14", ">-3.14", Query.Type.ALL);
    }

    @Test
    void testNegativesDecimalInRanges() {
        tester.assertParsed("normal:[-4.16;9.2]", "normal:[-4.16;9.2]", Query.Type.ALL);
        tester.assertParsed("[-4.16;9.2]", "[-4.16;9.2]", Query.Type.ALL);
        tester.assertParsed("normal:[-4.16;-9.2]", "normal:[-4.16;-9.2]", Query.Type.ALL);
        tester.assertParsed("[-4.16;-9.2]", "[-4.16;-9.2]", Query.Type.ALL);
    }

    @Test
    void testRangesAndNoise() {
        tester.assertParsed("[2;3]", "[2;3]]", Query.Type.ALL);
    }

    @Test
    void testIndexNoise() {
        tester.assertParsed("AND normal:a notanindex", "normal:a normal: notanindex:", Query.Type.ALL);
        tester.assertParsed(null, "normal:", Query.Type.ALL);
        tester.assertParsed(null, "normal:!", Query.Type.ALL);
        tester.assertParsed(null, "normal::", Query.Type.ALL);
        tester.assertParsed(null, "normal:_", Query.Type.ALL);
        tester.assertParsed(null, "normal:", Query.Type.ANY);
        tester.assertParsed(null, "normal:", Query.Type.ADVANCED);
    }

    @Test
    void testIndexNoiseAndExplicitPhrases() {
        tester.assertParsed("normal:\"a b\"", "normal:\" a b\"", Query.Type.ALL);
        tester.assertParsed("normal:\"a b\"", "normal:\"a b\"", Query.Type.ALL);
    }

    @Test
    void testExactMatchParsing1() {
        SearchDefinition sd = new SearchDefinition("testsd");

        Index index1 = new Index("testexact1");
        index1.setExact(true, null);
        sd.addIndex(index1);

        Index index2 = new Index("testexact2");
        index2.setExact(true, "()/aa*::*&");
        sd.addIndex(index2);

        IndexFacts indexFacts = new IndexFacts(new IndexModel(sd));
        ParsingTester customTester = new ParsingTester(indexFacts);

        customTester.assertParsed("testexact1:/,%&#", "testexact1:/,%&#", Query.Type.ALL);
        customTester.assertParsed("testexact2:/,%&#!!", "testexact2:/,%&#!!()/aa*::*&", Query.Type.ALL);
        customTester.assertParsed("AND word1 (OR testexact1:word2 testexact1:word3)", "word1 AND (testexact1:word2 OR testexact1:word3 )", Query.Type.ADVANCED);
        customTester.assertParsed("AND word (OR testexact1:AND testexact1:OR)", "word AND (testexact1: AND OR testexact1: OR )", Query.Type.ADVANCED);
    }

    /** Testing terminators containing control characters in conjunction with those control characters */
    @Test
    void testExactMatchParsing2() {
        SearchDefinition sd = new SearchDefinition("testsd");

        Index index1 = new Index("testexact1");
        index1.setExact(true, "*!*");
        sd.addIndex(index1);

        IndexFacts indexFacts = new IndexFacts(new IndexModel(sd));
        ParsingTester customTester = new ParsingTester(indexFacts);

        customTester.assertParsed("testexact1:_-_*!200", "testexact1:_-_*!**!!", Query.Type.ALL);
    }

    /** Testing terminators containing control characters in conjunction with those control characters */
    @Test
    void testExactMatchParsing3() {
        SearchDefinition sd = new SearchDefinition("testsd");

        Index index1 = new Index("testexact1");
        index1.setExact(true, "*");
        sd.addIndex(index1);

        IndexFacts indexFacts = new IndexFacts(new IndexModel(sd));
        ParsingTester customTester = new ParsingTester(indexFacts);

        customTester.assertParsed("testexact1:_-_*!200", "testexact1:_-_**!!", Query.Type.ALL);
    }

    // bug 1393139
    @Test
    void testMinimalBritneyFilter() {
        tester.assertParsed("RANK (+a -|c) b", "a RANK b", "-c", Query.Type.ADVANCED);
    }

    @Test
    void testBritneyFilter() {
        tester.assertParsed("RANK (+(AND performernameall:britney performernameall:spears songnameall:toxic |SongConsumable:1 |country:us |collapsedrecord:1 |doctype:song) -|collapsecount:0) (AND metadata:britney metadata:spears metadata:toxic)",
                "((performernameall:britney AND performernameall:spears AND songnameall:toxic) RANK (metadata:britney AND metadata:spears AND metadata:toxic))",
                "+SongConsumable:1 +country:us +collapsedrecord:1  -collapsecount:0  +doctype:song",
                Query.Type.ADVANCED);

        tester.assertParsed("AND (+(AND (RANK (OR (AND performernameall:britney performernameall:spears songnameall:toxic) (AND performernameall:britney performernameall:spears songnameall:toxic)) (AND metadata:britney metadata:spears metadata:toxic)) |SongConsumable:1 |country:us |collapsedrecord:1) -|collapsecount:0) |doctype:song",
                "(((performernameall:britney AND performernameall:spears AND songnameall:toxic) OR (performernameall:britney AND performernameall:spears AND songnameall:toxic)) RANK (metadata:britney AND metadata:spears AND metadata:toxic)))",
                "+SongConsumable:1 +country:us +collapsedrecord:1  -collapsecount:0  +doctype:song",
                Query.Type.ADVANCED);
    }

    // bug 1412840
    @Test
    void testGreedyPhrases() {
        tester.assertParsed("AND title:why title:\"1 2\"", "title:\"why\" title:\"&\" title:\"1/2\"", Query.Type.ALL);
    }

    // bug 1509347
    @Test
    void testUnderscoreInFieldNames() {
        tester.assertParsed("AND title:why score_under:what score_under:>5000",
                "title:why score_under:what score_under:>5000",
                Query.Type.ALL);
    }

    // bug 1509347
    @Test
    void testLeadingUnderscoreInFieldNames() {
        tester.assertParsed("AND title:why _under_score_:what _under_score_:>5000",
                "title:why _under_score_:what _under_score_:>5000",
                Query.Type.ALL);
    }

    // Re-add if underscore should be a word character
    // @Test
    // public void testUnderscoreAsWordCharacter() {
    //     tester.assertParsed("AND _a b_ a__b \"_a_b_c _d_e_f\"",
    //          "_a b_ a__b \"_a_b_c _d_e_f\"",
    //          Query.Type.ALL);
    // }

    // Re-add if underscore should be a word character
    // @Test
    // public void testUnderscoreAsWordWithIndexName() {
    //     tester.assertParsed("AND title:_a title:a title:_a_",
    //          "title:_a title:a title:_a_",
    //          Query.Type.ALL);
    // }

    // bug 524918
    @Test
    void testAdvancedSyntaxParensAndQuotes() {
        tester.assertParsed("OR a (AND \"b c d\" e)",
                "a OR (\"b (c) d\" AND e)",
                Query.Type.ADVANCED);
    }

    // This test is here instead of in the query parser because
    // this needs to become series of tests where the tokenizer
    // and parser will step on each other's toes.
    @Test
    void testStarFirstInAttributes() {
        tester.assertParsed("exactindex:*test",
                "exactindex:*test",
                Query.Type.ALL);

    }

    @Test
    void testOneWordWebParsing() {
        tester.assertParsed("a", "a", Query.Type.WEB);
    }

    @Test
    void testTwoWordWebParsing() {
        tester.assertParsed("AND a b", "a b", Query.Type.WEB);
    }

    @Test
    void testPlusWordWebParsing1() {
        Item root = tester.assertParsed("AND a b", "+a b", Query.Type.WEB);
        assertTrue(((AndItem) root).getItem(0).isProtected());
        assertFalse(((AndItem) root).getItem(1).isProtected());
    }

    @Test
    void testPlusWordWebParsing2() {
        Item root = tester.assertParsed("AND a b", "+a +b", Query.Type.WEB);
        assertTrue(((AndItem) root).getItem(0).isProtected());
        assertTrue(((AndItem) root).getItem(1).isProtected());
    }

    @Test
    void testNegativeWordsParsing1() {
        Item root = tester.assertParsed("+a -b", "a -b", Query.Type.WEB);
        assertFalse(((NotItem) root).getItem(0).isProtected());
        assertTrue(((NotItem) root).getItem(1).isProtected());
    }

    @Test
    void testNegativeWordsParsing2() {
        Item root = tester.assertParsed("+a -b", "+a -b", Query.Type.WEB);
        assertTrue(((NotItem) root).getItem(0).isProtected());
        assertTrue(((NotItem) root).getItem(1).isProtected());
    }

    @Test
    void testNegativeWordsParsing3() {
        tester.assertParsed("+a -b", "-b a", Query.Type.WEB);
    }

    @Test
    void testNegativeWordsParsing4() {
        tester.assertParsed("+(AND a b) -c -d", "a b -c -d", Query.Type.WEB);
    }

    @Test
    void testNegativeWordsParsing5() {
        tester.assertParsed("+(AND a \"b c\" d) -e -f", "a -e \"b c\" d -f", Query.Type.WEB);
    }

    @Test
    void testPhraseWebParsing() {
        tester.assertParsed("\"a b\"", "\"a b\"", Query.Type.WEB);
    }

    @Test
    void testPhraseAndExtraTermWebParsing() {
        tester.assertParsed("AND \"a b\" c", "\"a b\" c", Query.Type.WEB);
    }

    @Test
    void testNotOrWebParsing() {
        tester.assertParsed("AND a or b", "a or b", Query.Type.WEB);
    }

    @Test
    void testNotOrALLParsing() {
        tester.assertParsed("AND a OR b", "a OR b", Query.Type.ALL);
    }

    @Test
    void testOrParsing1() {
        tester.assertParsed("OR a b", "a OR b", Query.Type.WEB);
    }

    @Test
    void testOrParsing2() {
        tester.assertParsed("OR a b c", "a OR b OR c", Query.Type.WEB);
    }

    @Test
    void testOrParsing3() {
        tester.assertParsed("OR a (AND b c) \"d e\"", "a OR b c OR \"d e\"", Query.Type.WEB);
    }

    @Test
    void testOrParsing4() {
        tester.assertParsed("OR (AND or1 a) or2", "or1 a OR or2", Query.Type.WEB);
    }

    @Test
    void testOrCornerCase1() {
        tester.assertParsed("AND OR a", "OR a", Query.Type.WEB);
    }

    @Test
    void testOrCornerCase2() {
        tester.assertParsed("AND OR a", "OR a OR", Query.Type.WEB); // Don't care
    }

    @Test
    void testOrCornerCase3() {
        tester.assertParsed("AND OR a", "OR a OR OR", Query.Type.WEB); // Don't care
    }

    @Test
    void testOrCornerCase4() {
        tester.assertParsed("+(OR (AND a b) (AND d c) (AND g h)) -e -f", "a b OR d c -e -f OR g h", Query.Type.WEB);
    }

    @Test
    void testOdd1Web() {
        tester.assertParsed("AND window print error", "+window.print() +error", Query.Type.WEB);
    }

    @Test
    void testNotOnlyWeb() {
        tester.assertParsed(null, "-foobar", Query.Type.WEB);
    }

    @Test
    void testMultipleNotsOnltWeb() {
        tester.assertParsed(null, "-foo -bar -foobar", Query.Type.WEB);
    }

    @Test
    void testOnlyNotCompositeWeb() {
        tester.assertParsed(null, "-(foo bar baz)", Query.Type.WEB);
    }

    @Test
    void testSingleNegativeNumberLikeTermWeb() {
        tester.assertParsed("-12", "-12", Query.Type.WEB);
    }

    @Test
    void testSingleNegativeDecimalNumberLikeTermWeb() {
        tester.assertParsed("-12.2", "-12.2", Query.Type.WEB);
    }

    @Test
    void testDefaultWebIndices() {
        tester.assertParsed("AND notanindex b", "notanindex:b", Query.Type.WEB);
        tester.assertParsed("site:\"b $\"", "site:b", Query.Type.WEB);
        tester.assertParsed("hostname:b", "hostname:b", Query.Type.WEB);
        tester.assertParsed("link:b", "link:b", Query.Type.WEB);
        tester.assertParsed("url:b", "url:b", Query.Type.WEB);
        tester.assertParsed("inurl:b", "inurl:b", Query.Type.WEB);
        tester.assertParsed("intitle:b", "intitle:b", Query.Type.WEB);
    }

    @Test
    void testHTMLWeb() {
        tester.assertParsed("AND h2 Title h2", "<h2>Title</h2>", Query.Type.WEB);
    }

    /**
     * Shortcut terms are represented as any other terms, but can be rewritten downstream.
     * The information about added bangs is available from the origin as shown (do not use the weight to find this)
     */
    @Test
    void testShortcutsWeb() {
        tester.assertParsed("AND map new york", "map new york", Query.Type.WEB);

        AndItem root = (AndItem) tester.assertParsed("AND map!150 new york", "map! new york", Query.Type.WEB);
        assertEquals('!', ((WordItem) root.getItem(0)).getOrigin().charAfter(0));

        root = (AndItem) tester.assertParsed("AND barack obama news!150", "barack obama news!", Query.Type.WEB);
        assertEquals('!', ((WordItem) root.getItem(2)).getOrigin().charAfter(0));
    }

    @Test
    void testZipCodeShortcutWeb() {
        tester.assertParsed("12345", "12345", Query.Type.WEB);
        IntItem root = (IntItem) tester.assertParsed("00012!150", "00012!", Query.Type.WEB);
        assertEquals('!', root.getOrigin().charAfter(0));
    }

    @Test
    void testDouble() {
        Item number = tester.assertParsed("123456789.987654321", "123456789.987654321", Query.Type.ALL);
        assertTrue(number instanceof IntItem);
    }

    @Test
    void testLong() {
        Item number = tester.assertParsed("3000000000000", "3000000000000", Query.Type.ALL);
        assertTrue(number instanceof IntItem);
    }

    @Test
    void testNear1() {
        tester.assertParsed("NEAR(2) new york", "new NEAR york", Query.Type.ADVANCED);
    }

    @Test
    void testNear2() {
        tester.assertParsed("ONEAR(2) new york", "new ONEAR york", Query.Type.ADVANCED);
    }

    @Test
    void testNear3() {
        tester.assertParsed("NEAR(3) new york", "new NEAR(3) york", Query.Type.ADVANCED);
    }

    @Test
    void testNear4() {
        tester.assertParsed("ONEAR(3) new york", "new ONEAR(3) york", Query.Type.ADVANCED);
    }

    @Test
    void testNear5() {
        tester.assertParsed("NEAR(3) map new york", "map NEAR(3) new NEAR(3) york", Query.Type.ADVANCED);
    }

    @Test
    void testNear6() {
        tester.assertParsed("ONEAR(3) map new york", "map ONEAR(3) new ONEAR(3) york", Query.Type.ADVANCED);
    }

    @Test
    void testNear7() {
        tester.assertParsed("NEAR(4) (NEAR(3) map new) york", "map NEAR(3) new NEAR(4) york", Query.Type.ADVANCED);
    }

    @Test
    void testNear8() {
        tester.assertParsed("ONEAR(4) (ONEAR(3) map new) york", "map ONEAR(3) new ONEAR(4) york", Query.Type.ADVANCED);
    }

    @Test
    void testNearPrefix() {
        tester.assertParsed("NEAR(2) a b*", "a NEAR b*", Query.Type.ADVANCED);
    }

    @Test
    void testNestedBracesAndPhrases() {
        String userQuery = "(\"Secondary Curriculum\" (\"Key Stage 3\" OR KS3) (\"Key Stage 4\" OR KS4)) ";
        tester.assertParsed(
                "OR \"Secondary Curriculum\" \"Key Stage 3\" OR KS3 \"Key Stage 4\" OR KS4",
                userQuery, Query.Type.ALL);
        userQuery = "(\"Grande distribution\" (\"developpement durable\" OR \"commerce equitable\"))";
        tester.assertParsed(
                "OR \"Grande distribution\" \"developpement durable\" OR \"commerce equitable\"",
                userQuery, Query.Type.ALL);
        userQuery = "(\"road tunnel\" (\"tunnel management\" OR AID OR \"traffic systems\" OR supervision OR "
                + "\"decision aid system\") (Spie OR Telegra OR Telvent OR Steria)) ";
        tester.assertParsed(
                "OR \"road tunnel\" \"tunnel management\" OR AID OR \"traffic systems\" OR supervision OR \"decision aid system\" Spie OR Telegra OR Telvent OR Steria",
                userQuery, Query.Type.ALL);
    }

    @Test
    void testYetAnotherCycleQuery() {
        tester.assertParsed("+(OR (+d -f) b) -c",
                "( b -c  ( d -f )",
                Query.Type.ALL);
    }

    @Test
    void testSimpleEquivAdvanced() {
        tester.assertParsed("EQUIV foo bar baz", "foo equiv bar equiv baz", Query.Type.ADVANCED);
    }

    @Test
    void testEquivWordIntPhraseAdvanced() {
        tester.assertParsed("EQUIV foo 5 \"a b\"", "foo equiv 5 equiv \"a b\"", Query.Type.ADVANCED);
    }

    @Test
    void testEquivRejectCompositeAdvanced() {
        try {
            tester.assertParsed("this should not parse", "foo equiv (a or b)", Query.Type.ADVANCED);
        } catch (Exception e) {
            // Success
        }
    }

    @Test
    void testSimpleWandAdvanced() {
        tester.assertParsed("WEAKAND(100) foo bar baz", "foo wand bar wand baz", Query.Type.ADVANCED);
    }

    @Test
    void testSimpleWandAdvancedWithNonDefaultN() {
        tester.assertParsed("WEAKAND(32) foo bar baz", "foo weakand(32) bar weakand(32) baz", Query.Type.ADVANCED);
    }

    @Test
    void testSimpleWandAdvancedWithNonDefaultNAndWeights() {
        tester.assertParsed("WEAKAND(32) foo!32 bar!64 baz", "foo!32 weakand(32) bar!64 weakand(32) baz", Query.Type.ADVANCED);
    }

    @Test
    void testTwoRanges() {
        tester.assertParsed("AND score:[1.25;2.18] age:[25;30]", "score:[1.25;2.18] AND age:[25;30]", Query.Type.ADVANCED);
    }

    @Test
    void testTooLargeTermWeights() {
        // This behavior is a bit silly:
        tester.assertParsed("AND a 12345678901234567890", "a!12345678901234567890", Query.Type.ALL);
        // but in light of
        tester.assertParsed("AND a!150 b", "a!b", Query.Type.ALL);
        // which was the behavior when adding handling of too large term
        // weights, it is at least consistent. It should probably be implicit
        // phrases instead.
    }

    @Test
    void testAndSegmenting() {
        Item root = tester.parseQuery("a'b&c'd", Language.ENGLISH, Query.Type.ALL);
        assertTrue(root instanceof AndItem);
        AndItem top = (AndItem) root;
        assertTrue(top.getItem(0) instanceof AndSegmentItem);
        assertTrue(top.getItem(1) instanceof AndSegmentItem);
        AndSegmentItem seg1 = (AndSegmentItem) top.getItem(0);
        AndSegmentItem seg2 = (AndSegmentItem) top.getItem(1);
        Item t1 = seg1.getItem(0);
        Item t2 = seg1.getItem(1);
        Item t3 = seg2.getItem(0);
        Item t4 = seg2.getItem(1);
        assertTrue(((TaggableItem) t2).hasUniqueID());
        assertTrue(((TaggableItem) t3).hasUniqueID());
        assertEquals(((TaggableItem) t1).getConnectedItem(), t2);
        assertEquals(((TaggableItem) t2).getConnectedItem(), t3);
        assertEquals(((TaggableItem) t3).getConnectedItem(), t4);
    }

    @Test
    void testSiteAndSegmentPhrases() {
        tester.assertParsed("host.all:\"www abc com x y-z $\"",
                "host.all:www.abc.com/x'y-z", "",
                Query.Type.ALL, Language.ENGLISH);
    }

    @Test
    void testSiteAndSegmentPhrasesFollowedByText() {
        tester.assertParsed("AND host.all:\"www abc com x y-z $\" (SAND a b)",
                "host.all:www.abc.com/x'y-z a'b", "",
                Query.Type.ALL, Language.ENGLISH);
    }

    @Test
    void testIntItemFollowedByDot() {
        tester.assertParsed("AND Campion Ste 3 When To Her Lute Corinna Sings", "Campion Ste: 3. When To Her Lute Corinna Sings", Query.Type.ALL);
    }

    @Test
    void testNotIntItemIfPrecededByHyphen() {
        tester.assertParsed("AND Horny Horny '98 Radio Edit", "Horny [Horny '98 Radio Edit]]", Query.Type.ALL);
    }

    @Test
    void testNonAsciiNumber() {
        tester.assertParsed("AND title:199 title:119 title:201 title:149", "title:１９９．１１９．２０１．１４９", Query.Type.ALL);
    }

    @Test
    void testNoGrammar1() {
        tester.assertParsed("WEAKAND(100) foobar", "foobar", Query.Type.TOKENIZE);
    }

    @Test
    void testNoGrammar2() {
        tester.assertParsed("WEAKAND(100) foobar", "-foobar", Query.Type.TOKENIZE);
    }

    @Test
    void testNoGrammar3() {
        tester.assertParsed("WEAKAND(100) foo bar", "foo -bar", Query.Type.TOKENIZE);
    }

    @Test
    void testNoGrammar4() {
        tester.assertParsed("WEAKAND(100) foo bar baz one two 37", "foo -(bar baz \"one two\" 37)", Query.Type.TOKENIZE);
    }
}
