// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.vespa.test;

import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.*;
import com.yahoo.search.Query;
import com.yahoo.search.federation.vespa.QueryMarshaller;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.QueryTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QueryMarshallerTestCase {

    private static final Linguistics linguistics = new SimpleLinguistics();

    @Test
    public void testCommonCommonCase() {
        AndItem root = new AndItem();
        addThreeWords(root);
        assertEquals("a AND b AND c", new QueryMarshaller().marshal(root));
    }

    @Test
    public void testPhrase() {
        PhraseItem root = new PhraseItem();
        root.setIndexName("habla");
        addThreeWords(root);
        assertEquals("habla:\"a b c\"", new QueryMarshaller().marshal(root));
    }

    @Test
    public void testPhraseDefaultIndex() {
        PhraseItem root = new PhraseItem();
        addThreeWords(root);
        assertEquals("\"a b c\"", new QueryMarshaller().marshal(root));
    }

    @Test
    public void testLittleMoreComplex() {
        AndItem root = new AndItem();
        addThreeWords(root);
        OrItem ambig = new OrItem();
        root.addItem(ambig);
        addThreeWords(ambig);
        AndItem but = new AndItem();
        addThreeWords(but);
        ambig.addItem(but);
        assertEquals("a AND b AND c AND ( a OR b OR c OR ( a AND b AND c ) )",
                     new QueryMarshaller().marshal(root));
    }

    @Test
    public void testRank() {
        RankItem root = new RankItem();
        addThreeWords(root);
        assertEquals("a RANK b RANK c", new QueryMarshaller().marshal(root));
    }

    @Test
    public void testNear() {
        NearItem near = new NearItem(3);
        addThreeWords(near);
        assertEquals("a NEAR(3) b NEAR(3) c", new QueryMarshaller().marshal(near));
    }

    @Test
    public void testONear() {
        ONearItem oNear = new ONearItem(3);
        addThreeWords(oNear);
        assertEquals("a ONEAR(3) b ONEAR(3) c", new QueryMarshaller().marshal(oNear));
    }

    private void addThreeWords(CompositeItem root) {
        root.addItem(new WordItem("a"));
        root.addItem(new WordItem("b"));
        root.addItem(new WordItem("c"));
    }

    @Test
    public void testNegativeGroupedTerms() {
        testQueryString(new QueryMarshaller(), "a -(b c) -(d e)",
                        "a ANDNOT ( b AND c ) ANDNOT ( d AND e )");
    }

    @Test
    public void testPositiveGroupedTerms() {
        testQueryString(new QueryMarshaller(), "a (b c)", "a AND ( b OR c )");
    }

    @Test
    public void testInt() {
        testQueryString(new QueryMarshaller(), "yahoo 123", "yahoo AND 123");
    }

    @Test
    public void testCJKOneWord() {
        testQueryString(new QueryMarshaller(), "天龍人");
    }

    @Test
    public void testTwoWords() {
        testQueryString(new QueryMarshaller(), "John Smith", "John AND Smith", null, new SimpleLinguistics());
    }

    @Test
    public void testTwoWordsInPhrase() {
        testQueryString(new QueryMarshaller(), "\"John Smith\"", "\"John Smith\"", null, new SimpleLinguistics());
    }

    @Test
    public void testCJKTwoSentences() {
        testQueryString(new QueryMarshaller(), "是不是這樣的夜晚 你才會這樣地想起我", "是不是這樣的夜晚 AND 你才會這樣地想起我");
    }

    @Test
    public void testCJKTwoSentencesWithLanguage() {
        testQueryString(new QueryMarshaller(), "助妳好孕 生1胎北市發2萬", "助妳好孕 AND 生1胎北市發2萬", "zh-Hant");
    }

    @Test
    public void testCJKTwoSentencesInPhrase() {
        QueryMarshaller marshaller = new QueryMarshaller();
        testQueryString(marshaller, "\"助妳好孕 生1胎北市發2萬\"", "\"助妳好孕 生1胎北市發2萬\"", "zh-Hant");
        testQueryString(marshaller, "\"是不是這樣的夜晚 你才會這樣地想起我\"", "\"是不是這樣的夜晚 你才會這樣地想起我\"");
    }

    @Test
    public void testCJKMultipleSentences() {
        testQueryString(new QueryMarshaller(), "염부장님과 함께했던 좋은 추억들은", "염부장님과 AND 함께했던 AND 좋은 AND 추억들은");
    }

    @Test
    public void testIndexRestriction() {
        /** ticket 3707606, comment #29 */
        testQueryString(new QueryMarshaller(), "site:nytimes.com", "site:\"nytimes com\"");
    }

    private void testQueryString(QueryMarshaller marshaller, String uq) {
        testQueryString(marshaller, uq, uq, null);
    }

    private void testQueryString(QueryMarshaller marshaller, String uq, String mq) {
        testQueryString(marshaller, uq, mq, null);
    }

    private void testQueryString(QueryMarshaller marshaller, String uq, String mq, String lang) {
        testQueryString(marshaller, uq, mq, lang, linguistics);
    }

    private void testQueryString(QueryMarshaller marshaller, String uq, String mq, String lang, Linguistics linguistics) {
        Query query = new Query("/?query=" + QueryTestCase.httpEncode(uq) + ((lang != null) ? "&language=" + lang : ""));
        query.getModel().setExecution(new Execution(new Execution.Context(null, new IndexFacts(), null, null, linguistics)));
        assertEquals(mq, marshaller.marshal(query.getModel().getQueryTree().getRoot()));
    }

}
