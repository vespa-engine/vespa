// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.searcher.JuniperSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import com.yahoo.search.statistics.ElapsedTimeTestCase;
import com.yahoo.search.statistics.ElapsedTimeTestCase.CreativeTimeSource;
import com.yahoo.search.statistics.TimeTracker;
import com.yahoo.text.Utf8;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the XML renderer
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
public class XMLRendererTestCase {

    @Test
    void testGetEncoding() {
        XmlRenderer renderer = new XmlRenderer();
        renderer.init();
        assertEquals("utf-8", renderer.getEncoding());
    }

    @Test
    void testGetMimeType() {
        XmlRenderer renderer = new XmlRenderer();
        renderer.init();
        assertEquals("text/xml", renderer.getMimeType());
    }

    @Test
    void testXmlRendering() throws Exception {
        Query q = new Query("/?query=a");

        Result result = new Result(q);
        result.setCoverage(new Coverage(500, 1, 1));

        FastHit h = new FastHit("http://localhost/", .95);
        h.setField("$a", "Hello, world.");
        h.setField("b", "foo");
        result.hits().add(h);

        HitGroup g = new HitGroup("usual");
        h = new FastHit("http://localhost/1", .90);
        h.setField("c", "d");
        g.add(h);
        result.hits().add(g);

        HitGroup gg = new HitGroup("type grouphit");
        gg.types().add("grouphit");
        gg.setField("e", "f");
        result.hits().add(gg);
        result.hits().addError(ErrorMessage.createInternalServerError("message"));

        String summary = render(result);

        String expected =
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "<result total-hit-count=\"0\" coverage-docs=\"500\" coverage-nodes=\"1\" coverage-full=\"false\" coverage=\"0\" results-full=\"0\" results=\"1\">\n" +
                        "  <error code=\"18\">Internal server error.</error>\n" +
                        "  <errordetails>\n" +
                        "    <error error=\"Internal server error.\" code=\"18\">message</error>\n" +
                        "  </errordetails>\n" +
                        "  <group relevancy=\"1.0\">\n" +
                        "    <hit type=\"summary\" relevancy=\"0.9\">\n" +
                        "      <field name=\"relevancy\">0.9</field>\n" +
                        "      <field name=\"c\">d</field>\n" +
                        "    </hit>\n" +
                        "  </group>\n" +
                        "  <hit type=\"grouphit\" relevancy=\"1.0\">\n" +
                        "    <id>type grouphit</id>\n" +
                        "  </hit>\n" +
                        "  <hit type=\"summary\" relevancy=\"0.95\">\n" +
                        "    <field name=\"relevancy\">0.95</field>\n" +
                        "    <field name=\"b\">foo</field>\n" +
                        "  </hit>\n" +
                        "</result>\n";

        assertEquals(expected, summary);
    }

    @Test
    void testXmlRenderingOfDynamicSummary() throws Exception {
        String content = "\uFFF9Feeding\uFFFAfeed\uFFFB \u001F\uFFF9documents\uFFFAdocument\uFFFB\u001F into Vespa \uFFF9is\uFFFAbe\u001Eincrement of a set of \u001F\uFFF9documents\uFFFAdocument\uFFFB\u001F fed into Vespa \uFFF9is\u001Efloat in XML when \u001Fdocument\u001F attribute \uFFF9is\uFFFAbe\uFFFB int\u001E";
        Result result = createResult("one", content, true);

        String summary = render(result);

        String expected =
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "<result total-hit-count=\"0\">\n" +
                        "  <hit relevancy=\"1.0\">\n" +
                        "    <field name=\"relevancy\">1.0</field>\n" +
                        "    <field name=\"sddocname\">one</field>\n" +
                        "    <field name=\"dynteaser\">Feeding <hi>documents</hi> into Vespa is<sep />increment of a set of <hi>documents</hi> fed into Vespa <sep />float in XML when <hi>document</hi> attribute is int<sep /></field>\n" +
                        "  </hit>\n" +
                        "</result>\n";
        assertEquals(expected, summary);
    }

    @Test
    void testXmlRenderingWithTimeTracking() throws Exception {
        Query q = new Query("/?query=a&tracelevel=5");
        q.getPresentation().setTiming(true);

        Result result = new Result(q);
        result.setCoverage(new Coverage(500, 1, 1));

        TimeTracker t = new TimeTracker(new Chain<Searcher>(new NoopSearcher("first"),
                new NoopSearcher("second"),
                new NoopSearcher("third")));
        ElapsedTimeTestCase.doInjectTimeSource(t, new CreativeTimeSource(new long[]{1L, 2L, 3L, 4L, 5L, 6L, 7L}));
        t.sampleSearch(0, true);
        t.sampleSearch(1, true);
        t.sampleSearch(2, true);
        t.sampleSearch(3, true);
        t.sampleSearchReturn(2, true, null);
        t.sampleSearchReturn(1, true, null);
        t.sampleSearchReturn(0, true, null);
        result.getElapsedTime().add(t);

        String summary = render(result);

        assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n<result total-hit-count=\"0\"",
                summary.substring(0, 67));
        assertTrue(summary.contains("querytime="));
        assertTrue(summary.contains("summaryfetchtime="));
        assertTrue(summary.contains("searchtime="));
        assertTrue(summary.contains("<meta type=\"context\">"));
    }

    private String render(Result result) throws Exception {
        XmlRenderer renderer = new XmlRenderer();
        renderer.init();
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        CompletableFuture<Boolean> f = renderer.renderResponse(bs, result, null, null);
        assertTrue(f.get());
        return Utf8.toString(bs.toByteArray());
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
        return new Chain<>(searcher, docsource);
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

    private Execution createExecution(Chain<Searcher> chain) {
        IndexModel indexModel = new IndexModel(createSearchDefinitionOne());
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

    public static class NoopSearcher extends Searcher {

        public NoopSearcher(String name) {
            super(new ComponentId(name));
        }

        @Override
        public Result search(Query query, Execution execution) {
            return execution.search(query);
        }

    }

}
