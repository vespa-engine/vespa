// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import com.yahoo.prelude.searcher.JuniperSearcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests juniper highlighting
 *
 * @author Steinar Knutsen
 */
public class JuniperSearcherTestCase {

    /**
     * Creates a search chain which always returns a result with one hit containing information given in this
     *
     * @param sdName the search definition type of the returned hit
     * @param content the content of the "dynteaser" field of the returned hit
     */
    private Chain<Searcher> createSearchChain(String sdName, String content) {
        JuniperSearcher searcher = new JuniperSearcher(new ComponentId("test"),
                                                       new QrSearchersConfig(new QrSearchersConfig.Builder()));

        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        addResult(new Query("?query=12"), sdName, content, docsource);
        addResult(new Query("?query=12&bolding=false"), sdName, content, docsource);
        return new Chain<Searcher>(searcher, docsource);
    }

    private void addResult(Query query, String sdName, String content, DocumentSourceSearcher docsource) {
        Result r = new Result(query);
        FastHit hit = new FastHit();
        hit.setId("http://abc.html");
        hit.setRelevance(new Relevance(1));
        hit.setField(Hit.SDDOCNAME_FIELD, sdName);
        hit.setField("dynteaser", content);
        r.hits().add(hit);
        docsource.addResult(query, r);
    }

    /** Creates a result of the search definiton "one" */
    private Result createResult(String content) {
        return createResult("one", content, true);
    }

    private Result createResult(String sdName, String content) {
        return createResult(sdName, content, true);
    }

    private Result createResult(String sdName, String content, boolean bolding) {
        Chain<Searcher> chain = createSearchChain(sdName, content);
        Query query = new Query("?query=12");
        if ( ! bolding)
            query = new Query("?query=12&bolding=false");
        Execution execution = createExecution(chain);
        Result result = execution.search(query);
        execution.fill(result);
        return result;
    }

    private Execution createExecution(Chain<Searcher> chain) {
        Map<String, List<String>> clusters = new LinkedHashMap<>();
        Map<String, SearchDefinition> searchDefs = new LinkedHashMap<>();
        searchDefs.put("one", createSearchDefinitionOne());
        searchDefs.put("two", createSearchDefinitionTwo());
        SearchDefinition union = new SearchDefinition("union");
        IndexModel indexModel = new IndexModel(clusters, searchDefs, union);
        return new Execution(chain, Execution.Context.createContextStub(new IndexFacts(indexModel)));
    }

    private SearchDefinition createSearchDefinitionOne() {
        SearchDefinition one = new SearchDefinition("one");

        Index dynteaser = new Index("dynteaser");
        dynteaser.setDynamicSummary(true);
        one.addIndex(dynteaser);

        Index bigteaser = new Index("bigteaser");
        dynteaser.setHighlightSummary(true);
        one.addIndex(bigteaser);

        Index otherteaser = new Index("otherteaser");
        otherteaser.setDynamicSummary(true);
        one.addIndex(otherteaser);

        return one;
    }

    private SearchDefinition createSearchDefinitionTwo() {
        SearchDefinition two = new SearchDefinition("two");
        return two;
    }

    @Test
    public void testFieldRewriting() {
        Result check = createResult("\u001FXYZ\u001F\u001EQWE\u001FJKL\u001FASD&");
        assertEquals(1, check.getHitCount());
        assertEquals("<hi>XYZ</hi><sep />QWE<hi>JKL</hi>ASD&",
                     check.hits().get(0).getField("dynteaser").toString());
        check = createResult("a&b&c");
        assertEquals(1, check.getHitCount());
        assertEquals("a&b&c",
                     check.hits().get(0).getField("dynteaser").toString());
    }

    @Test
    public void testNoRewritingDueToSearchDefinition() {
        Result check = createResult("two", "\u001FXYZ\u001F\u001EQWE\u001FJKL\u001FASD&");
        assertEquals(1, check.getHitCount());
        assertEquals("\u001FXYZ\u001F\u001EQWE\u001FJKL\u001FASD&",
                check.hits().get(0).getField("dynteaser").toString());
        check = createResult("a&b&c");
        assertEquals(1, check.getHitCount());
        assertEquals("a&b&c",
                check.hits().get(0).getField("dynteaser").toString());
    }

    @Test
    public void testBoldingEquals() {
        assertFalse(new Query("?query=12").equals(new Query("?query=12&bolding=false")));
    }

    @Test
    public void testUnboldedRewriting() {
        Result check = createResult("one", "\u001FXYZ\u001F\u001EQWE\u001FJKL\u001FASD&", false);
        assertEquals(1, check.getHitCount());
        assertEquals("XYZ...QWEJKLASD&",
                     check.hits().get(0).getField("dynteaser").toString());
    }

    @Test
    public void testAnnotatedSummaryFields() {
        Result check = createResult("\uFFF9Feeding\uFFFAfeed\uFFFB \u001F\uFFF9documents\uFFFAdocument\uFFFB\u001F into Vespa \uFFF9is\uFFFAbe\u001Eincrement of a set of \u001F\uFFF9documents\uFFFAdocument\uFFFB\u001F fed into Vespa \uFFF9is\u001Efloat in XML when \u001Fdocument\u001F attribute \uFFF9is\uFFFAbe\uFFFB int\u001E");
        assertEquals(1, check.getHitCount());
        assertEquals("Feeding <hi>documents</hi> into Vespa is<sep />increment of a set of <hi>documents</hi> fed into Vespa <sep />float in XML when <hi>document</hi> attribute is int<sep />", check.hits().get(0).getField("dynteaser").toString());

        check = createResult("one", "\uFFF9Feeding\uFFFAfeed\uFFFB \u001F\uFFF9documents\uFFFAdocument\uFFFB\u001F into Vespa \uFFF9is\uFFFAbe\u001Eincrement of a set of \u001F\uFFF9documents\uFFFAdocument\uFFFB\u001F fed into Vespa \uFFF9is\u001Efloat in XML when \u001Fdocument\u001F attribute \uFFF9is\uFFFAbe\uFFFB int\u001E", false);
        assertEquals(1, check.getHitCount());
        assertEquals("Feeding documents into Vespa is...increment of a set of documents fed into Vespa ...float in XML when document attribute is int...", check.hits().get(0).getField("dynteaser").toString());

        check = createResult("\u001ecommon the term \uFFF9is\uFFFAbe\uFFFB within the set of \u001f\uFFF9documents\uFFFAdocument\uFFFB\u001f. Hence, unusual \uFFF9terms\uFFFAterm\uFFFB or \uFFF9phrases\uFFFAphrase\u001eadded\uFFFAadd\uFFFB to as a remedy). Each of the \u001fdocument\u001f \uFFF9fields\uFFFAfield\uFFFB in a catalog can be \uFFF9given\u001e");
        assertEquals(1, check.getHitCount());
        assertEquals("<sep />common the term is within the set of <hi>documents</hi>. Hence, unusual terms or phrases<sep /> to as a remedy). Each of the <hi>document</hi> fields in a catalog can be <sep />", check.hits().get(0).getField("dynteaser").toString());

        check = createResult("\u001e\uFFF9is\uFFFAbe\uFFFB within the set of \u001f\uFFF9documents\uFFFAdocument\uFFFB\u001f. \uFFF9phrases\uFFFAphrase\uFFFB\u001E\uFFFAadd\uFFFB to as a remedy). Each of the \u001fdocument\u001f \uFFF9fields\uFFFAfield\uFFFB in a catalog can be \uFFF9given\uFFFA\u001e");
        assertEquals(1, check.getHitCount());
        assertEquals("<sep />is within the set of <hi>documents</hi>. phrases<sep /> to as a remedy). Each of the <hi>document</hi> fields in a catalog can be given<sep />", check.hits().get(0).getField("dynteaser").toString());

        check = createResult("\u001eis\uFFFAbe\uFFFB within the set of \u001f\uFFF9documents\uFFFAdocument\uFFFB\u001f. \uFFF9phrases\uFFFAphrase\u001Eadd\uFFFB to as a remedy). Each of the \u001fdocument\u001f \uFFF9fields\uFFFAfield\uFFFB in a catalog can be \uFFF9given\u001e");
        assertEquals(1, check.getHitCount());
        assertEquals("<sep /> within the set of <hi>documents</hi>. phrases<sep /> to as a remedy). Each of the <hi>document</hi> fields in a catalog can be <sep />", check.hits().get(0).getField("dynteaser").toString());

        check = createResult("\u001e\uFFFAbe\uFFFB within the set of \u001f\uFFF9documents\uFFFAdocument\uFFFB\u001f. \uFFF9phrases\uFFFA\u001E\uFFFA\uFFFB to as a remedy). Each of the \u001fdocument\u001f \uFFF9fields\uFFFAfield\uFFFB in a catalog can be \uFFF9\u001e");
        assertEquals(1, check.getHitCount());
        assertEquals("<sep /> within the set of <hi>documents</hi>. phrases<sep /> to as a remedy). Each of the <hi>document</hi> fields in a catalog can be <sep />", check.hits().get(0).getField("dynteaser").toString());

        check = createResult("\u001e\uFFFAbe\uFFFB within the set of \u001f\uFFF9documents\uFFFAdocument\uFFFB\u001f\uFFF9phrases\uFFFA\u001E\uFFFA\uFFFB to as a remedy). Each of the \u001fdocument\u001f \uFFF9fields\uFFFAfield\uFFFB in a catalog can be \uFFF9\u001e");
        assertEquals(1, check.getHitCount());
        assertEquals("<sep /> within the set of <hi>documents</hi>phrases<sep /> to as a remedy). Each of the <hi>document</hi> fields in a catalog can be <sep />", check.hits().get(0).getField("dynteaser").toString());

        check = createResult("\u001e\uFFFAbe\uFFFB within the set of \uFFF9documents\uFFFAdocument\uFFFB\uFFF9phrases\uFFFA\u001E\uFFFA\uFFFB to as a remedy). Each of the \u001fdocument\u001f \uFFF9fields\uFFFAfield\uFFFB in a catalog can be \uFFF9\u001e");
        assertEquals(1, check.getHitCount());
        assertEquals("<sep /> within the set of documentsphrases<sep /> to as a remedy). Each of the <hi>document</hi> fields in a catalog can be <sep />", check.hits().get(0).getField("dynteaser").toString());
    }

    @Test
    public void testThatIncompleteAnnotationWithHighlightIsIgnored() {
        // Look at bug 5707026 for details.
        {
            Result check = createResult("of\u001e\u001fyahoo\u001f\uFFFB! \uFFF9Angels\uFFFAangels\uFFFB \uFFF9\u001fYahoo\u001f\uFFFA\u001fyahoo\u001f\uFFFB! \uFFF9Angles\uFFFAangels\uFFFB \uFFF9is\uFFFAbe\u001e");
            assertEquals(1, check.getHitCount());
            assertEquals("of<sep />! Angels <hi>Yahoo</hi>! Angles is<sep />",
                    check.hits().get(0).getField("dynteaser").toString());
        }
        {
            Result check = createResult("\u001e\u001fY\u001f\uFFFA\u001fy\u001f\uFFFB! \uFFF9News\uFFFAnews\uFFFB \uFFF9RSS\uFFFArss\uFFFB \uFFF9\u001fY\u001f\uFFFA\u001fy\u001f\uFFFB!\u001e");
            assertEquals(1, check.getHitCount());
            assertEquals("<sep />! News RSS <hi>Y</hi>!<sep />",
                    check.hits().get(0).getField("dynteaser").toString());
        }
    }

    @Test
    public void testThatIncompleteAnnotationWithHighlightAtTheBeginningIsIgnored() {
        {
            Result check = createResult("\u001e\u001fIncomplete\uFFFAincomplete\uFFFB\u001f \uFFF9Original\uFFFAstemmed\uFFFB\u001e");
            assertEquals(1, check.getHitCount());
            assertEquals("<sep /> Original<sep />", check.hits().get(0).getField("dynteaser").toString());
        }
        {
            Result check = createResult("\u001e\u001f\uFFFAincomplete\uFFFB\u001f \uFFF9Original\uFFFAstemmed\uFFFB\u001e");
            assertEquals(1, check.getHitCount());
            assertEquals("<sep /> Original<sep />", check.hits().get(0).getField("dynteaser").toString());
        }
        {
            Result check = createResult("\u001e\u001fincomplete\uFFFB\u001f \uFFF9Original\uFFFAstemmed\uFFFB\u001e");
            assertEquals(1, check.getHitCount());
            assertEquals("<sep /> Original<sep />", check.hits().get(0).getField("dynteaser").toString());
        }
    }

    @Test
    public void testThatIncompleteAnnotationWithHighlightAtTheEndIsIgnored() {
        {
            Result check = createResult("\u001e\uFFF9Original\uFFFAstemmed\uFFFB \u001f\uFFF9Incomplete\uFFFAincomplete\u001f\u001e");
            assertEquals(1, check.getHitCount());
            assertEquals("<sep />Original <sep />", check.hits().get(0).getField("dynteaser").toString());
        }
        {
            Result check = createResult("\u001e\uFFF9Original\uFFFAstemmed\uFFFB \u001f\uFFF9Incomplete\uFFFA\u001f\u001e");
            assertEquals(1, check.getHitCount());
            assertEquals("<sep />Original <sep />", check.hits().get(0).getField("dynteaser").toString());
        }
        {
            Result check = createResult("\u001e\uFFF9Original\uFFFAstemmed\uFFFB \u001f\uFFF9Incomplete\u001f\u001e");
            assertEquals(1, check.getHitCount());
            assertEquals("<sep />Original <sep />", check.hits().get(0).getField("dynteaser").toString());
        }
    }

    @Test
    public void testExplicitTwoPhase() {
        Chain<Searcher> searchChain = createSearchChain("one", "\u001e\uFFFAbe\uFFFB within the set of \u001f\uFFF9documents\uFFFAdocument\uFFFB\u001f. \uFFF9phrases\uFFFA\u001E\uFFFA\uFFFB to as a remedy). Each of the \u001fdocument\u001f \uFFF9fields\uFFFAfield\uFFFB in a catalog can be \uFFF9\u001e");
        Query q = new Query("?query=12");
        Result check = createExecution(searchChain).search(q);
        assertEquals(1, check.getHitCount());
        assertNull(check.hits().get(0).getField("dynteaser"));
        createExecution(searchChain).fill(check);
        assertEquals(1, check.getHitCount());
        assertEquals("<sep /> within the set of <hi>documents</hi>. phrases<sep /> to as a remedy). Each of the <hi>document</hi> fields in a catalog can be <sep />", check.hits().get(0).getField("dynteaser").toString());
    }

    @Test
    public void testCompoundWordsBolding() {
        Result check = createResult("\u001eTest \u001fkommunikations\u001f\u001ffehler\u001f");
        assertEquals(1, check.getHitCount());
        assertEquals("<sep />Test <hi>kommunikationsfehler</hi>",  check.hits().get(0).getField("dynteaser").toString());
    }

}
