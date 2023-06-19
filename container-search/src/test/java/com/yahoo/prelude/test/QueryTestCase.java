// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.test;

import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.query.BoolItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for query class
 *
 * @author Bjorn Borud
 */
public class QueryTestCase {

    @Test
    void testBoolItem() {
        var original = new BoolItem(false);
        var cloned = original.clone();
        assertNotSame(original, cloned);
        assertEquals(original, cloned);
    }

    @Test
    void testSimpleQueryParsing() {
        Query q = newQuery("/search?query=foobar&offset=10&hits=20&type=all");
        assertEquals("foobar", ((WordItem) q.getModel().getQueryTree().getRoot()).getWord());
        assertEquals(10, q.getOffset());
        assertEquals(20, q.getHits());
    }

    @Test
    void testAdvancedQueryParsing() {
        Query q = newQuery("/search?query=fOObar and kanoo&offset=10&hits=20&filter=-foo +bar&type=adv&suggestonly=true");
        assertEquals("AND (+(AND fOObar kanoo) -|foo) |bar", q.getModel().getQueryTree().getRoot().toString());
        assertEquals(10, q.getOffset());
        assertEquals(20, q.getHits());
        assertEquals(true, q.properties().getBoolean("suggestonly", false));
    }

    @Test
    void testAnyQueryParsing() {
        Query q = newQuery("/search?query=foobar and kanoo&offset=10&hits=10&type=any&suggestonly=true&filter=-fast.type:offensive&encoding=latin1");
        assertEquals("+(OR foobar and kanoo) -|fast.type:offensive", q.getModel().getQueryTree().getRoot().toString());
        assertEquals(10, q.getOffset());
        assertEquals(10, q.getHits());
        assertEquals(true, q.properties().getBoolean("suggestonly", false));
        assertEquals("latin1", q.getModel().getEncoding());
    }

    @Test
    void testLongQueryParsing() {
        Query q = newQuery("/p13n?query=news"
                + "interest:cnn!254+interest:cnnfn!171+interest:cnn+"
                + "financial!96+interest:"
                + "yahoo+com!253+interest:www+yahoo!138+"
                + "interest:www+yahoo+com!136"
                + "&hits=20&offset=0&vectorranking=queryrank");
        assertEquals("/p13n", q.getUri().getPath());
        assertEquals(0, q.getOffset());
        assertEquals(20, q.getHits());
        assertEquals("queryrank", q.properties().get("vectorranking"));
    }

    /**
     * Test that the integer convenience wrapper works as documented,
     * throwing NumberFormatException when applied to something that
     * is not a number.
     */
    @Test
    void testGetParamInt() {
        Query q = newQuery("/search?query=foo%20bar&someint=10&notint=hello");
        assertEquals(10, (int) q.properties().getInteger("someint"));

        // provoke an exception.  if exception is not triggered
        // we fail the test.
        try {
            q.properties().getInteger("notint");
            fail("Trying to access non-integer as integer should fail");
        } catch (java.lang.NumberFormatException e) {
            // NOP
        }
    }

    @Test
    void testUtf8Decoding() {
        Query q = new Query("/?query=beyonc%C3%A9&type=all");
        assertEquals("beyonc\u00e9", ((WordItem) q.getModel().getQueryTree().getRoot()).getWord());
    }

    @Test
    void testSortSpec() {
        Query q = newQuery("?query=test&sortspec=+a -b c +[rank]");
        assertNotNull(q.getRanking().getSorting());
        List<Sorting.FieldOrder> sortSpec = q.getRanking().getSorting().fieldOrders();
        assertEquals(sortSpec.size(), 4);
        assertEquals(Sorting.Order.ASCENDING, sortSpec.get(0).getSortOrder());
        assertEquals("a", sortSpec.get(0).getFieldName());
        assertEquals(Sorting.Order.DESCENDING, sortSpec.get(1).getSortOrder());
        assertEquals("b", sortSpec.get(1).getFieldName());
        assertEquals(Sorting.Order.UNDEFINED, sortSpec.get(2).getSortOrder());
        assertEquals("c", sortSpec.get(2).getFieldName());
        assertEquals(Sorting.Order.ASCENDING, sortSpec.get(3).getSortOrder());
        assertEquals("[rank]", sortSpec.get(3).getFieldName());
    }

    @Test
    void testSortSpecLowerCase() {
        Query q = newQuery("?query=test&sortspec=-lowercase(name)");
        assertNotNull(q.getRanking().getSorting());
        List<Sorting.FieldOrder> sortSpec = q.getRanking().getSorting().fieldOrders();
        assertEquals(sortSpec.size(), 1);
        assertEquals(Sorting.Order.DESCENDING,
                sortSpec.get(0).getSortOrder());
        assertEquals("name", sortSpec.get(0).getFieldName());
        assertTrue(sortSpec.get(0).getSorter() instanceof Sorting.LowerCaseSorter);
    }

    public void checkUcaUS(String spec) {
        Query q = newQuery(spec);
        assertNotNull(q.getRanking().getSorting());
        List<Sorting.FieldOrder> sortSpec = q.getRanking().getSorting().fieldOrders();
        assertEquals(sortSpec.size(), 1);
        assertEquals(Sorting.Order.DESCENDING,
                     sortSpec.get(0).getSortOrder());
        assertTrue(sortSpec.get(0).getSorter() instanceof Sorting.UcaSorter);
        assertEquals("name", sortSpec.get(0).getFieldName());
    }

    @Test
    void testSortSpecUca() {
        checkUcaUS("?query=test&sortspec=-uca(name,en_US)");
        checkUcaUS("?query=test&sortspec=-UCA(name,en_US)");
        checkSortSpecUcaUSOptional("?query=test&sortspec=-uca(name,en_US,tertiary)");
        checkSortSpecUcaUSOptional("?query=test&sortspec=-uca(name,en_US,TERTIARY)");
    }

    @Test
    void testInvalidSortFunction() {
        assertQueryError(
                "?query=test&sortspec=-ucca(name,en_US)",
                "Could not set 'ranking.sorting' to '-ucca(name,en_US)': Unknown sort function 'ucca'");
    }

    @Test
    void testMissingSortFunction() {
        assertQueryError(
                "?query=test&sortspec=-(name)",
                "Could not set 'ranking.sorting' to '-(name)': No sort function specified");
    }

    @Test
    void testInvalidUcaStrength() {
        assertQueryError(
                "?query=test&sortspec=-uca(name,en_US,tertary)",
                "Could not set 'ranking.sorting' to '-uca(name,en_US,tertary)': Unknown collation strength: 'tertary'");
    }

    public void checkSortSpecUcaUSOptional(String spec) {
        Query q = newQuery(spec);
        assertNotNull(q.getRanking().getSorting());
        List<Sorting.FieldOrder> sortSpec = q.getRanking().getSorting().fieldOrders();
        assertEquals(sortSpec.size(), 1);
        assertEquals(Sorting.Order.DESCENDING,
                     sortSpec.get(0).getSortOrder());
        assertTrue(sortSpec.get(0).getSorter() instanceof Sorting.UcaSorter);
        assertEquals(((Sorting.UcaSorter)sortSpec.get(0).getSorter()).getLocale(), "en_US" );
        assertEquals(((Sorting.UcaSorter)sortSpec.get(0).getSorter()).getStrength(), Sorting.UcaSorter.Strength.TERTIARY );
        assertEquals("name", sortSpec.get(0).getFieldName());
    }

    /**
     * Check query hash function.
     * Extremely simple for now, will be used for pathological cases.
     */
    @Test
    void testHashCode() {
        Query p = newQuery("?query=foo&type=any");
        Query q = newQuery("?query=foo&type=all");
        assertTrue(p.hashCode() != q.hashCode());
    }

    /** Test using the defaultindex feature */
    @Test
    void testDefaultIndex() {
        Query q = newQuery("?query=hi hello keyword:kanoo " +
                "default:munkz \"phrases too\"&default-index=def");
        assertEquals("WEAKAND(100) def:hi def:hello keyword:kanoo " +
                "default:munkz def:\"phrases too\"",
                q.getModel().getQueryTree().getRoot().toString());
    }

    /** Test that GET parameter names are case in-sensitive */
    @Test
    void testGETParametersCase() {
        Query q = newQuery("?QUERY=testing&hits=10&oFfSeT=10");

        assertEquals("testing", q.getModel().getQueryString());
        assertEquals(10, q.getHits());
        assertEquals(10, q.getOffset());
    }


    @Test
    void testNegativeHitValue() {
        assertQueryError(
                "?query=test&hits=-1",
                "Could not set 'hits' to '-1': Must be a positive number");
    }

    @Test
    void testNaNHitValue() {
        assertQueryError(
                "?query=test&hits=NaN",
                "Could not set 'hits' to 'NaN': 'NaN' is not a valid integer");
    }

    @Test
    void testNoneHitValue() {
        assertQueryError(
                "?query=test&hits=(none)",
                "Could not set 'hits' to '(none)': '(none)' is not a valid integer");
    }

    @Test
    void testNegativeOffsetValue() {
        assertQueryError(
                "?query=test&offset=-1",
                "Could not set 'offset' to '-1': Must be a positive number");
    }

    @Test
    void testNaNOffsetValue() {
        assertQueryError(
                "?query=test&offset=NaN",
                "Could not set 'offset' to 'NaN': 'NaN' is not a valid integer");
    }

    @Test
    void testNoneOffsetValue() {
        assertQueryError(
                "?query=test&offset=(none)",
                "Could not set 'offset' to '(none)': '(none)' is not a valid integer");
    }

    @Test
    void testNoneHitsNegativeOffsetValue() {
        assertQueryError(
                "?query=test&hits=(none)",
                "Could not set 'hits' to '(none)': '(none)' is not a valid integer");
    }

    @Test
    void testFeedbackIsTransferredToResult() {
        assertQueryError(
                "?query=test&hits=(none)",
                "Could not set 'hits' to '(none)': '(none)' is not a valid integer");
    }

    @Test
    void testUnicodeNormalization() {
        Linguistics linguistics = new SimpleLinguistics();
        Query query = newQueryFromEncoded("?query=content:%EF%BC%B3%EF%BC%AF%EF%BC%AE%EF%BC%B9&type=all", Language.ENGLISH,
                linguistics);
        assertEquals("SONY", ((WordItem) query.getModel().getQueryTree().getRoot()).getWord());

        query = newQueryFromEncoded("?query=foo&filter=+%EF%BC%B3%EF%BC%AF%EF%BC%AE%EF%BC%B9&type=all", Language.ENGLISH,
                linguistics);
        assertEquals("RANK foo |SONY", query.getModel().getQueryTree().getRoot().toString());

        query = newQueryFromEncoded("?query=foo+AND+%EF%BC%B3%EF%BC%AF%EF%BC%AE%EF%BC%B9)&type=adv",
                Language.ENGLISH, linguistics);
        assertEquals("AND foo SONY", query.getModel().getQueryTree().getRoot().toString());
    }

    /** Test a vertical specific patch, see Tokenizer */
    @Test
    @Disabled
    void testPrivateUseCharacterParsing() {
        Query query = newQuery("?query=%EF%89%AB");
        assertEquals(Character.UnicodeBlock.PRIVATE_USE_AREA,
                Character.UnicodeBlock.of(query.getModel().getQueryTree().getRoot().toString().charAt(0)));
    }

    /** Test a vertical specific patch, see Tokenizer */
    @Test
    @Disabled
    void testOtherCharactersParsing() {
        Query query = newQuery(com.yahoo.search.test.QueryTestCase.httpEncode("?query=\u3007\u2e80\u2eff\u2ed0"));
        assertEquals(Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION,
                Character.UnicodeBlock.of(query.getModel().getQueryTree().getRoot().toString().charAt(0)));
        assertEquals(Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT,
                Character.UnicodeBlock.of(query.getModel().getQueryTree().getRoot().toString().charAt(1)));
        assertEquals(Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT,
                Character.UnicodeBlock.of(query.getModel().getQueryTree().getRoot().toString().charAt(2)));
        assertEquals(Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT,
                Character.UnicodeBlock.of(query.getModel().getQueryTree().getRoot().toString().charAt(3)));
    }

    @Test
    void testFreshness() {
        Query query = newQuery("?query=test&datetime=103");
        assertEquals(query.getRanking().getFreshness().getRefTime(), 103);
        query.getRanking().setFreshness("193");

        assertEquals(query.getRanking().getFreshness().getRefTime(), 193);
        query.getRanking().setFreshness("now");

        assertTrue(query.getRanking().getFreshness().getSystemTimeInSecondsSinceEpoch() >= query.getRanking().getFreshness().getRefTime());
        int presize = query.errors().size();
        query.getRanking().setFreshness("sometimeslater");

        int postsize = query.errors().size();
        assertTrue(postsize > presize);
    }

    @Test
    void testCopy() {
        Query qs = newQuery("?query=test&rankfeature.something=2");
        assertEquals("WEAKAND(100) test", qs.getModel().getQueryTree().toString());
        assertEquals((int) qs.properties().getInteger("rankfeature.something"), 2);
        Query qp = new Query(qs);
        assertEquals("WEAKAND(100) test", qp.getModel().getQueryTree().getRoot().toString());
        assertFalse(qp.getRanking().getFeatures().isEmpty());
        assertEquals(2.0, qp.getRanking().getFeatures().getDouble("something").getAsDouble(), 0.000001);
    }

    private Query newQuery(String queryString) {
        return newQuery(queryString, null, new SimpleLinguistics());
    }

    private Query newQuery(String queryString, Language language, Linguistics linguistics) {
        return newQueryFromEncoded(com.yahoo.search.test.QueryTestCase.httpEncode(queryString), language, linguistics);
    }

    private IndexFacts createIndexFacts() {
        SearchDefinition sd = new SearchDefinition("test");
        sd.addIndex(new Index("fast.type"));
        sd.addIndex(new Index("def"));
        sd.addIndex(new Index("default"));
        sd.addIndex(new Index("keyword"));
        sd.addIndex(new Index("content"));
        return new IndexFacts(new IndexModel(sd));
    }

    private Query newQueryFromEncoded(String encodedQueryString, Language language, Linguistics linguistics) {
        Query query = new Query(encodedQueryString);
        query.getModel().setExecution(new Execution(Execution.Context.createContextStub(createIndexFacts(),
                                                                                        linguistics)));
        query.getModel().setLanguage(language);
        return query;
    }

    private void assertQueryError(String queryString, String expectedErrorMessage) {
        try {
            newQuery(queryString);
            fail("Above statement should throw");
        } catch (IllegalArgumentException e) {
            // As expected.
            assertEquals(expectedErrorMessage, Exceptions.toMessageString(e));
        }
    }

}

