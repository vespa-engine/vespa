// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import static org.junit.Assert.*;

import com.yahoo.language.Language;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.search.grouping.GroupingRequest;

import org.apache.http.client.utils.URIBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.yahoo.collections.Tuple2;
import com.yahoo.component.Version;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.Sorting.AttributeSorter;
import com.yahoo.search.query.Sorting.FieldOrder;
import com.yahoo.search.query.Sorting.LowerCaseSorter;
import com.yahoo.search.query.Sorting.Order;
import com.yahoo.search.query.Sorting.UcaSorter;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Smoke test for first generation YQL+ integration.
 */
public class MinimalQueryInserterTestCase {

    private Chain<Searcher> searchChain;
    private Execution.Context context;
    private Execution execution;

    @Before
    public void setUp() throws Exception {
        searchChain = new Chain<>(new MinimalQueryInserter());
        context = Execution.Context.createContextStub(null, null, new SimpleLinguistics());
        execution = new Execution(searchChain, context);
    }

    @After
    public void tearDown() throws Exception {
        searchChain = null;
        context = null;
        execution = null;
    }

    @Test
    public void requireThatGroupingStepsAreAttachedToQuery() {
        URIBuilder builder = new URIBuilder();
        builder.setPath("search/");

        builder.setParameter("yql", "select foo from bar where baz contains 'cox';");
        Query query = new Query(builder.toString());
        execution.search(query);
        assertEquals("baz:cox", query.getModel().getQueryTree().toString());
        assertGrouping("[]", query);

        assertEquals(1, query.getPresentation().getSummaryFields().size());
        assertEquals("foo", query.getPresentation().getSummaryFields().toArray(new String[1])[0]);

        builder.setParameter("yql", "select foo from bar where baz contains 'cox' " +
                                    "| all(group(a) each(output(count())));");
        query = new Query(builder.toString());
        execution.search(query);
        assertEquals("baz:cox", query.getModel().getQueryTree().toString());
        assertGrouping("[[]all(group(a) each(output(count())))]", query);

        builder.setParameter("yql", "select foo from bar where baz contains 'cox' " +
                                    "| all(group(a) each(output(count()))) " +
                                    "| all(group(b) each(output(count())));");
        query = new Query(builder.toString());
        execution.search(query);
        assertEquals("baz:cox", query.getModel().getQueryTree().toString());
        assertGrouping("[[]all(group(a) each(output(count())))," +
                       " []all(group(b) each(output(count())))]", query);
    }

    @Test
    public void requireThatGroupingContinuationsAreAttachedToQuery() {
        URIBuilder builder = new URIBuilder();
        builder.setPath("search/");

        builder.setParameter("yql", "select foo from bar where baz contains 'cox';");
        Query query = new Query(builder.toString());
        execution.search(query);
        assertEquals("baz:cox", query.getModel().getQueryTree().toString());
        assertGrouping("[]", query);

        builder.setParameter("yql", "select foo from bar where baz contains 'cox' " +
                                    "| [{ 'continuations':['BCBCBCBEBG', 'BCBKCBACBKCCK'] }]" +
                                    "all(group(a) each(output(count())));");
        query = new Query(builder.toString());
        execution.search(query);
        assertEquals("baz:cox", query.getModel().getQueryTree().toString());
        assertGrouping("[[BCBCBCBEBG, BCBKCBACBKCCK]all(group(a) each(output(count())))]", query);

        builder.setParameter("yql", "select foo from bar where baz contains 'cox' " +
                                    "| [{ 'continuations':['BCBCBCBEBG', 'BCBKCBACBKCCK'] }]" +
                                    "all(group(a) each(output(count()))) " +
                                    "| [{ 'continuations':['BCBBBBBDBF', 'BCBJBPCBJCCJ'] }]" +
                                    "all(group(b) each(output(count())));");
        query = new Query(builder.toString());
        execution.search(query);
        assertEquals("baz:cox", query.getModel().getQueryTree().toString());
        assertGrouping("[[BCBCBCBEBG, BCBKCBACBKCCK]all(group(a) each(output(count())))," +
                       " [BCBBBBBDBF, BCBJBPCBJCCJ]all(group(b) each(output(count())))]", query);
    }

    @Test
    @Ignore
    // TODO: YQL work in progress (jon)
    public void testTmp() {
        Query query = new Query("search/?query=easilyRecognizedString&yql=select%20ignoredfield%20from%20ignoredsource%20where%20title%20contains%20%22madonna%22%20and%20userQuery()%3B");
        //execution.search(query);
        assertEquals("AND title:madonna easilyRecognizedString", query.getModel().getQueryTree().toString());
    }

    @Test
    public void testSearch() {
        Query query = new Query("search/?query=easilyRecognizedString&yql=select%20ignoredfield%20from%20ignoredsource%20where%20title%20contains%20%22madonna%22%20and%20userQuery()%3B");
        execution.search(query);
        assertEquals("AND title:madonna easilyRecognizedString", query.getModel().getQueryTree().toString());
        assertEquals(Language.ENGLISH, query.getModel().getParsingLanguage());
    }

    @Test
    public void testExplicitLanguageIsHonoredWithVerbatimQuery() {
        String japaneseWord = "\u30ab\u30bf\u30ab\u30ca";
        Query query = new Query("search/?language=ja" + "&yql=select%20ignoredField%20from%20ignoredsource%20where%20title%20contains%20%22" + encode(japaneseWord) + "%22%3B");
        execution.search(query);
        assertEquals(Language.JAPANESE, query.getModel().getParsingLanguage());
        assertEquals("title:"+ japaneseWord, query.getModel().getQueryTree().toString());
    }

    @Test
    public void testUserLanguageIsDetectedWithVerbatimQuery() {
        String japaneseWord = "\u30ab\u30bf\u30ab\u30ca";
        Query query = new Query("search/?yql=select%20ignoredField%20from%20ignoredsource%20where%20title%20contains%20%22" + encode(japaneseWord) + "%22%3B");
        execution.search(query);
        assertEquals(Language.JAPANESE, query.getModel().getParsingLanguage());
        assertEquals("title:"+ japaneseWord, query.getModel().getQueryTree().toString());
    }

    @Test
    public void testUserLanguageIsDetectedWithUserInput() {
        String japaneseWord = "\u30ab\u30bf\u30ab\u30ca";
        Query query = new Query("search/?userString=" + encode(japaneseWord) + "&yql=select%20ignoredfield%20from%20ignoredsource%20where%20title%20contains%20%22madonna%22%20and%20userInput(@userString)%3B");
        execution.search(query);
        assertEquals(Language.JAPANESE, query.getModel().getParsingLanguage());
        assertEquals("AND title:madonna default:" + japaneseWord, query.getModel().getQueryTree().toString());
    }

    @Test
    public void testUserLanguageIsDetectedWithUserQuery() {
        String japaneseWord = "\u30ab\u30bf\u30ab\u30ca";
        Query query = new Query("search/?query=" + encode(japaneseWord) + "&yql=select%20ignoredfield%20from%20ignoredsource%20where%20title%20contains%20%22madonna%22%20and%20userQuery()%3B");
        execution.search(query);
        assertEquals(Language.JAPANESE, query.getModel().getParsingLanguage());
        assertEquals("AND title:madonna " + japaneseWord, query.getModel().getQueryTree().toString());
    }

    @Test
    public void testUserQueryFailsWithoutArgument() {
        Query query = new Query("search/?query=easilyRecognizedString&yql=select%20ignoredfield%20from%20ignoredsource%20where%20title%20contains%20%22madonna%22%20and%20userQuery()%3B");
        execution.search(query);
        assertEquals("AND title:madonna easilyRecognizedString", query.getModel().getQueryTree().toString());
    }

    @Test
    public void testSearchFromAllSourcesWithUserSource() {
        Query query = new Query("search/?query=easilyRecognizedString&sources=abc&yql=select%20ignoredfield%20from%20sources%20*%20where%20title%20contains%20%22madonna%22%20and%20userQuery()%3B");
        execution.search(query);
        assertEquals("AND title:madonna easilyRecognizedString", query.getModel().getQueryTree().toString());
        assertEquals(0, query.getModel().getSources().size());
    }

    @Test
    public void testSearchFromAllSourcesWithoutUserSource() {
        Query query = new Query("search/?query=easilyRecognizedString&yql=select%20ignoredfield%20from%20sources%20*%20where%20title%20contains%20%22madonna%22%20and%20userQuery()%3B");
        execution.search(query);
        assertEquals("AND title:madonna easilyRecognizedString", query.getModel().getQueryTree().toString());
        assertEquals(0, query.getModel().getSources().size());
    }

    @Test
    public void testSearchFromSomeSourcesWithoutUserSource() {
        Query query = new Query("search/?query=easilyRecognizedString&yql=select%20ignoredfield%20from%20sources%20sourceA,%20sourceB%20where%20title%20contains%20%22madonna%22%20and%20userQuery()%3B");
        execution.search(query);
        assertEquals("AND title:madonna easilyRecognizedString", query.getModel().getQueryTree().toString());
        assertEquals(2, query.getModel().getSources().size());
        assertTrue(query.getModel().getSources().contains("sourceA"));
        assertTrue(query.getModel().getSources().contains("sourceB"));
    }

    @Test
    public void testSearchFromSomeSourcesWithUserSource() {
        Query query = new Query("search/?query=easilyRecognizedString&sources=abc&yql=select%20ignoredfield%20from%20sources%20sourceA,%20sourceB%20where%20title%20contains%20%22madonna%22%20and%20userQuery()%3B");
        execution.search(query);
        assertEquals("AND title:madonna easilyRecognizedString", query.getModel().getQueryTree().toString());
        assertEquals(3, query.getModel().getSources().size());
        assertTrue(query.getModel().getSources().contains("sourceA"));
        assertTrue(query.getModel().getSources().contains("sourceB"));
        assertTrue(query.getModel().getSources().contains("abc"));
    }

    @Test
    public final void testSearchFromSomeSourcesWithOverlappingUserSource() {
        final Query query = new Query("search/?query=easilyRecognizedString&sources=abc,sourceA&yql=select%20ignoredfield%20from%20sources%20sourceA,%20sourceB%20where%20title%20contains%20%22madonna%22%20and%20userQuery()%3B");
        execution.search(query);
        assertEquals("AND title:madonna easilyRecognizedString", query.getModel().getQueryTree().toString());
        assertEquals(3, query.getModel().getSources().size());
        assertTrue(query.getModel().getSources().contains("sourceA"));
        assertTrue(query.getModel().getSources().contains("sourceB"));
        assertTrue(query.getModel().getSources().contains("abc"));
    }

    @Test
    public void testLimitAndOffset() {
        Query query = new Query("search/?yql=select%20*%20from%20sources%20*%20where%20title%20contains%20%22madonna%22%20limit%2031offset%207%3B");
        execution.search(query);
        assertEquals(7, query.getOffset());
        assertEquals(24, query.getHits());
        assertEquals("select * from sources * where title contains \"madonna\" limit 31 offset 7;",
                     query.yqlRepresentation());
    }

    @Test
    public void testMaxOffset() {
        Query query = new Query("search/?yql=select%20*%20from%20sources%20*%20where%20title%20contains%20%22madonna%22%20limit%2040031offset%2040000%3B");
        Result r = execution.search(query);
        assertEquals(1, r.hits().getErrorHit().errors().size());
        ErrorMessage e = r.hits().getErrorHit().errorIterator().next();
        assertEquals(com.yahoo.container.protect.Error.INVALID_QUERY_PARAMETER.code, e.getCode());
        assertTrue(e.getDetailedMessage().indexOf("max offset") >= 0);
    }

    @Test
    public void testMaxLimit() {
        Query query = new Query("search/?yql=select%20*%20from%20sources%20*%20where%20title%20contains%20%22madonna%22%20limit%2040000offset%207%3B");
        Result r = execution.search(query);
        assertEquals(1, r.hits().getErrorHit().errors().size());
        ErrorMessage e = r.hits().getErrorHit().errorIterator().next();
        assertEquals(com.yahoo.container.protect.Error.INVALID_QUERY_PARAMETER.code, e.getCode());
        assertTrue(e.getDetailedMessage().indexOf("max hits") >= 0);
    }

    @Test
    public void testTimeout() {
        Query query = new Query("search/?yql=select%20*%20from%20sources%20*%20where%20title%20contains%20%22madonna%22%20timeout%2051%3B");
        execution.search(query);
        assertEquals(51L, query.getTimeout());
        assertEquals("select * from sources * where title contains \"madonna\" timeout 51;", query.yqlRepresentation());
    }

    @Test
    public void testOrdering() {
        {
            String yql = "select%20ignoredfield%20from%20ignoredsource%20where%20title%20contains%20%22madonna%22%20order%20by%20something%2C%20shoesize%20desc%20limit%20300%20timeout%203%3B";
            Query query = new Query("search/?yql=" + yql);
            execution.search(query);
            assertEquals(2, query.getRanking().getSorting().fieldOrders()
                    .size());
            assertEquals("something", query.getRanking().getSorting()
                    .fieldOrders().get(0).getFieldName());
            assertEquals(Order.ASCENDING, query.getRanking().getSorting()
                    .fieldOrders().get(0).getSortOrder());
            assertEquals("shoesize", query.getRanking().getSorting()
                    .fieldOrders().get(1).getFieldName());
            assertEquals(Order.DESCENDING, query.getRanking().getSorting()
                    .fieldOrders().get(1).getSortOrder());
            assertEquals("select ignoredfield from ignoredsource where title contains \"madonna\" order by something, shoesize desc limit 300 timeout 3;", query.yqlRepresentation());
        }
        {
            String yql = "select%20ignoredfield%20from%20ignoredsource%20where%20title%20contains%20%22madonna%22%20order%20by%20other%20limit%20300%20timeout%203%3B";
            Query query = new Query("search/?yql=" + yql);
            execution.search(query);
            assertEquals("other", query.getRanking().getSorting().fieldOrders()
                    .get(0).getFieldName());
            assertEquals(Order.ASCENDING, query.getRanking().getSorting()
                    .fieldOrders().get(0).getSortOrder());
            assertEquals("select ignoredfield from ignoredsource where title contains \"madonna\" order by other limit 300 timeout 3;", query.yqlRepresentation());
        }
        {
            String yql = "select%20foo%20from%20bar%20where%20title%20contains%20%22madonna%22%20order%20by%20%5B%7B%22function%22%3A%20%22uca%22%2C%20%22locale%22%3A%20%22en_US%22%2C%20%22strength%22%3A%20%22IDENTICAL%22%7D%5Dother%20desc%2C%20%5B%7B%22function%22%3A%20%22lowercase%22%7D%5Dsomething%20limit%20300%20timeout%203%3B";
            Query query = new Query("search/?yql=" + yql);
            execution.search(query);
            {
                FieldOrder fieldOrder = query.getRanking().getSorting().fieldOrders().get(0);
                assertEquals("other", fieldOrder.getFieldName());
                assertEquals(Order.DESCENDING, fieldOrder.getSortOrder());
                AttributeSorter sorter = fieldOrder.getSorter();
                assertEquals(UcaSorter.class, sorter.getClass());
                UcaSorter uca = (UcaSorter) sorter;
                assertEquals("en_US", uca.getLocale());
                assertEquals(UcaSorter.Strength.IDENTICAL, uca.getStrength());
            }
            {
                FieldOrder fieldOrder = query.getRanking().getSorting().fieldOrders().get(1);
                assertEquals("something", fieldOrder.getFieldName());
                assertEquals(Order.ASCENDING, fieldOrder.getSortOrder());
                AttributeSorter sorter = fieldOrder.getSorter();
                assertEquals(LowerCaseSorter.class, sorter.getClass());
            }
            assertEquals("select foo from bar where title contains \"madonna\" order by [{\"function\": \"uca\", \"locale\": \"en_US\", \"strength\": \"IDENTICAL\"}]other desc, [{\"function\": \"lowercase\"}]something limit 300 timeout 3;",
                    query.yqlRepresentation());
        }
    }

    @Test
    public void testStringRepresentation() {
        String yql = "select%20ignoredfield%20from%20ignoredsource%20where%20title%20contains%20%22madonna%22%20order%20by%20something%2C%20shoesize%20desc%20limit%20300%20timeout%203%3B";
        Query query = new Query("search/?yql=" + yql);
        execution.search(query);
        assertEquals("select ignoredfield from ignoredsource where title contains \"madonna\" order by something, shoesize desc limit 300 timeout 3;",
                     query.yqlRepresentation());
    }


    private static void assertGrouping(String expected, Query query) {
        List<String> actual = new ArrayList<>();
        for (GroupingRequest request : query.getSelect().getGrouping())
            actual.add(request.continuations().toString() + request.getRootOperation());
        assertEquals(expected, actual.toString());
    }

    private String encode(String s) {
        try {
            return URLEncoder.encode(s, "utf-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Will never happen");
        }
    }

}
