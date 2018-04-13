// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.vespa.test;

import com.yahoo.prelude.query.*;
import com.yahoo.search.Query;
import com.yahoo.search.federation.vespa.VespaSearcher;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.Parser;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import org.apache.http.HttpEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.Assert.assertEquals;

/**
 * Check query marshaling in VespaSearcher works.
 *
 * @author Steinar Knutsen
 */
public class VespaSearcherTestCase {

    private VespaSearcher searcher;

    @Before
    protected void setUp() {
        searcher = new VespaSearcher("cache1","",0,"");
    }

    @After
    protected void tearDown() {
        searcher.deconstruct();
    }

    @Test
    public void testMarshalQuery() {
        RankItem root = new RankItem();
        QueryTree r = new QueryTree(root);
        AndItem recall = new AndItem();
        PhraseItem usual = new PhraseItem();
        PhraseItem filterPhrase = new PhraseItem(new String[] {"bloody", "expensive"});
        WordItem filterWord = new WordItem("silly");

        filterPhrase.setFilter(true);
        filterWord.setFilter(true);

        root.addItem(recall);
        usual.addItem(new WordItem("new"));
        usual.addItem(new WordItem("york"));
        recall.addItem(usual);
        recall.addItem(new WordItem("shoes"));
        root.addItem(new WordItem("nike"));
        root.addItem(new WordItem("adidas"));
        root.addItem(filterPhrase);
        recall.addItem(filterWord);

        assertEquals("( \"new york\" AND shoes AND silly ) RANK nike RANK adidas RANK \"bloody expensive\"", searcher.marshalQuery(r));
    }

    @Test
    public void testMarshalQuerySmallTree() {
        RankItem root = new RankItem();
        QueryTree r = new QueryTree(root);
        AndItem recall = new AndItem();
        PhraseItem usual = new PhraseItem();
        PhraseItem filterPhrase = new PhraseItem(new String[] {"bloody", "expensive"});
        WordItem filterWord = new WordItem("silly");

        filterPhrase.setFilter(true);
        filterWord.setFilter(true);

        root.addItem(recall);
        usual.addItem(new WordItem("new"));
        usual.addItem(new WordItem("york"));
        recall.addItem(usual);
        recall.addItem(new WordItem("shoes"));
        root.addItem(filterPhrase);
        recall.addItem(filterWord);

        assertEquals("( \"new york\" AND shoes AND silly ) RANK \"bloody expensive\"", searcher.marshalQuery(r));
        // TODO: Switch to this 2-way check rather than just 1-way and then also make this actually treat filter terms correctly
        // assertMarshals(root)
    }

    @Test
    public void testWandMarshalling() {
        WeakAndItem root = new WeakAndItem();
        root.setN(32);
        root.addItem(new WordItem("a"));
        root.addItem(new WordItem("b"));
        root.addItem(new WordItem("c"));
        assertMarshals(root);
    }

    @Test
    public void testWandMarshalling2() {
        // AND (WAND(10) a!1 the!10) source:yahoonews
        AndItem root = new AndItem();
        WeakAndItem wand = new WeakAndItem(10);
        wand.addItem(newWeightedWordItem("a",1));
        wand.addItem(newWeightedWordItem("the",10));
        root.addItem(wand);
        root.addItem(new WordItem("yahoonews","source"));
        assertMarshals(root);
    }

    private WordItem newWeightedWordItem(String word,int weight) {
        WordItem wordItem=new WordItem(word);
        wordItem.setWeight(weight);
        return wordItem;
    }

    private void assertMarshals(Item root) {
        QueryTree r = new QueryTree(root);
        String marshalledQuery=searcher.marshalQuery(r);
        assertEquals("Marshalled form '" + marshalledQuery + "' recreates the original",
                     r,parseQuery(marshalledQuery,""));
    }

    private static Item parseQuery(String query, String filter) {
        Parser parser = ParserFactory.newInstance(Query.Type.ADVANCED, new ParserEnvironment());
        return parser.parse(new Parsable().setQuery(query).setFilter(filter));
    }

    @Test
    public void testSourceProviderProperties() throws Exception {
        /* TODO: update test
        Server httpServer = new Server();
        try {
            SocketConnector listener = new SocketConnector();
            listener.setHost("0.0.0.0");
            httpServer.addConnector(listener);
            httpServer.setHandler(new DummyHandler());
            httpServer.start();

            int port=httpServer.getConnectors()[0].getLocalPort();

            List<SourcesConfig.Source> sourcesConfig = new ArrayList<SourcesConfig.Source>();
            SourcesConfig.Source sourceConfig = new SourcesConfig.Source();
            sourceConfig.chain.setValue("news");
            sourceConfig.provider.setValue("news");
            sourceConfig.id.setValue("news");
            sourceConfig.timelimit.value = 10000;
            sourcesConfig.add(sourceConfig);
            FederationSearcher federator =
                    new FederationSearcher(ComponentId.createAnonymousComponentId(),
                            new ArrayList<SourcesConfig.Source>(sourcesConfig));
            SearchChain mainChain=new OrderedSearchChain(federator);

            SearchChainRegistry registry=new SearchChainRegistry();
            SearchChain sourceChain=new SearchChain(new ComponentId("news"),new VespaSearcher("test","localhost",port,""));
            registry.register(sourceChain);
            Query query=new Query("?query=hans&hits=20&provider.news.a=a1&source.news.b=b1");
            Result result=new Execution(mainChain,registry).search(query);
            assertNull(result.hits().getError());
            Hit testHit=result.hits().get("testHit");
            assertNotNull(testHit);
            assertEquals("testValue",testHit.fields().get("testField"));
            assertEquals("a1",testHit.fields().get("a"));
            assertEquals("b1",testHit.fields().get("b"));
        }
        finally {
            httpServer.stop();
        }
        */
    }

    @Test
    public void testVespaSearcher() {
        VespaSearcher v=new VespaSearcherValidatingSubclass();
        new Execution(v, Execution.Context.createContextStub()).search(new Query(com.yahoo.search.test.QueryTestCase.httpEncode("?query=test&filter=myfilter")));
    }

    private class VespaSearcherValidatingSubclass extends VespaSearcher {

        public VespaSearcherValidatingSubclass() {
            super("configId","host",80,"path");
        }

        @Override
        protected HttpEntity getEntity(URI uri, Hit requestMeta, Query query) throws IOException {
            assertEquals("http://host:80/path?query=test+RANK+myfilter&type=adv&offset=0&hits=10&presentation.format=xml",uri.toString());
            return super.getEntity(uri,requestMeta,query);
        }

    }

    //  used by the old testSourceProviderProperties()
//    private class DummyHandler extends AbstractHandler {
//        public void handle(String s, Request request, HttpServletRequest httpServletRequest,
//                           HttpServletResponse httpServletResponse) throws IOException, ServletException {
//
//            try {
//                Response httpResponse = httpServletResponse instanceof Response ? (Response) httpServletResponse : HttpConnection.getCurrentConnection().getResponse();
//
//                httpResponse.setStatus(HttpStatus.OK_200);
//                httpResponse.setContentType("text/xml");
//                httpResponse.setCharacterEncoding("UTF-8");
//                Result r=new Result(new Query());
//                Hit testHit=new Hit("testHit");
//                testHit.setField("uri","testHit"); // That this is necessary is quite unfortunate...
//                testHit.setField("testField","testValue");
//                // Write back all incoming properties:
//                for (Object e : httpServletRequest.getParameterMap().entrySet()) {
//                    Map.Entry entry=(Map.Entry)e;
//                    testHit.setField(entry.getKey().toString(),getFirstValue(entry.getValue()));
//                }
//
//                r.hits().add(testHit);
//
//                //StringWriter sw=new StringWriter();
//                //r.render(sw);
//                //System.out.println(sw.toString());
//
//                SearchRendererAdaptor.callRender(httpResponse.getWriter(), r);
//                httpResponse.complete();
//            }
//            catch (Exception e) {
//                System.out.println("WARNING: Could not respond to request: " + Exceptions.toMessageString(e));
//                e.printStackTrace();
//            }
//        }
//
//        private String getFirstValue(Object entry) {
//            if (entry instanceof String[])
//                return ((String[])entry)[0].toString();
//            else
//                return entry.toString();
//        }
//    }

}
