// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.federation.FederationConfig;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.searcher.BlendingSearcher;
import com.yahoo.prelude.searcher.FillSearcher;
import com.yahoo.search.Searcher;
import com.yahoo.search.federation.FederationSearcher;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.RootGroup;
import com.yahoo.search.grouping.result.StringBucketId;
import com.yahoo.search.grouping.result.StringId;
import com.yahoo.search.grouping.result.ValueGroupId;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChain;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the BlendingSearcher class
 *
 * @author Bob Travis
 * @author bratseth
 */
@SuppressWarnings({ "rawtypes" })
public class BlendingSearcherTestCase {

    private static final double delta = 0.00000001;

    public class BlendingSearcherWrapper extends Searcher {

        private SearchChain blendingChain;
        private final FederationConfig.Builder builder = new FederationConfig.Builder();
        private final Map<String, Searcher> searchers = new HashMap<>();
        private SearchChainRegistry chainRegistry = new SearchChainRegistry();

        private final String blendingField;

        public BlendingSearcherWrapper() {
            blendingField = null;
        }

        public BlendingSearcherWrapper(String blendingField) {
            this.blendingField = blendingField;
        }

        @SuppressWarnings("serial")
        public BlendingSearcherWrapper(QrSearchersConfig cfg) {
            QrSearchersConfig.Com.Yahoo.Prelude.Searcher.BlendingSearcher s = cfg.com().yahoo().prelude().searcher().BlendingSearcher();
            blendingField = s.docid().length() > 0 ? s.docid() : null;
        }

        /** Adds a source implemented as a single searcher */
        public void addSource(String sourceName, Searcher searcher) {
            addSource(createSearchChain(new ComponentId(sourceName), searcher));
        }

        /** Adds a source implemented as a chain */
        public void addSource(Chain<Searcher> chain) {
            String sourceName = chain.getId().stringValue();
            builder.target(new FederationConfig.Target.Builder().id(sourceName)
                                                                .searchChain(new FederationConfig.Target.SearchChain.Builder()
                                                                                     .searchChainId(sourceName).timeoutMillis(10000)
                                                                                     .useByDefault(true))
            );
            chainRegistry.register(chain);
        }

        @Override
        public Result search(Query query, Execution execution) {
            query.setTimeout(10000);
            query.setOffset(query.getOffset());
            query.setHits(query.getHits());
            Execution exec = new Execution(blendingChain, Execution.Context.createContextStub(chainRegistry));
            exec.context().populateFrom(execution.context());
            return exec.search(query);
        }

        @Override
        public void fill(Result result, String summaryClass, Execution execution) {
            new Execution(blendingChain, Execution.Context.createContextStub(chainRegistry)).fill(result, summaryClass);
        }

        public boolean initialize() {
            // First add all the current searchers as searchchains
            for(Map.Entry<String, Searcher> entry : searchers.entrySet()) {
                chainRegistry.register(
                        createSearchChain(
                                new ComponentId(entry.getKey()),
                                entry.getValue()));
            }

            FederationSearcher fedSearcher =
                    new FederationSearcher(new FederationConfig(builder), new ComponentRegistry<>());
            BlendingSearcher blendingSearcher = new BlendingSearcher(blendingField);
            blendingChain = new SearchChain(ComponentId.createAnonymousComponentId("blendingChain"), blendingSearcher, fedSearcher);
            return true;
        }

        private SearchChain createSearchChain(ComponentId chainId, Searcher searcher) {
            return new SearchChain(chainId, searcher);
        }
    }

    @Test
    void testitTwoPhase() {
        DocumentSourceSearcher chain1 = new DocumentSourceSearcher();
        DocumentSourceSearcher chain2 = new DocumentSourceSearcher();
        DocumentSourceSearcher chain3 = new DocumentSourceSearcher();

        Query q = new Query("/search?query=hannibal");

        Result r1 = new Result(q);
        Result r2 = new Result(q);
        Result r3 = new Result(q);

        r1.setTotalHitCount(13);
        r1.hits().add(new Hit("http://host1.com", 101){
            {
                setSource("one");
            }
        });
        r1.hits().add(new Hit("http://host2.com", 102){
            {
                setSource("one");
            }
        });
        r1.hits().add(new Hit("http://host3.com", 103){
            {
                setSource("one");
            }
        });
        chain1.addResult(q, r1);

        r2.setTotalHitCount(17);
        r2.hits().add(new Hit("http://host1.com", 101){
            {
                setSource("two");
            }
        });
        r2.hits().add(new Hit("http://host2.com", 102){
            {
                setSource("two");
            }
        });
        r2.hits().add(new Hit("http://host4.com", 104){
            {
                setSource("two");
            }
        });
        chain2.addResult(q, r2);

        r3.setTotalHitCount(37);
        r3.hits().add(new Hit("http://host5.com", 100){
            {
                setSource("three");
            }
        });
        r3.hits().add(new Hit("http://host6.com", 106){
            {
                setSource("three");
            }
        });
        r3.hits().add(new Hit("http://host7.com", 105){
            {
                setSource("three");
            }
        });
        chain3.addResult(q, r3);

        BlendingSearcherWrapper blender1 = new BlendingSearcherWrapper();
        blender1.addSource("one", chain1);
        blender1.initialize();
        q.setWindow(0, 10);
        Result br1 = new Execution(blender1, Execution.Context.createContextStub()).search(q);
        assertEquals(3, br1.getHitCount());
        assertEquals(13, br1.getTotalHitCount());
        assertEquals("http://host3.com/", br1.hits().get(0).getId().toString());

        BlendingSearcherWrapper blender2 = new BlendingSearcherWrapper();
        blender2.addSource("two", chain1);
        blender2.addSource("three", chain2);
        blender2.initialize();
        q.setWindow(0, 10);
        Result br2 = new Execution(blender2, Execution.Context.createContextStub()).search(q);
        assertEquals(6, br2.getHitCount());
        assertEquals(30, br2.getTotalHitCount());
        assertEquals("http://host4.com/", br2.hits().get(0).getId().toString());

        BlendingSearcherWrapper blender3 = new BlendingSearcherWrapper();
        blender3.addSource("four", chain1);
        blender3.addSource("five", chain2);
        blender3.addSource("six", chain3);
        blender3.initialize();
        q.setWindow(0, 10);
        Result br3 = new Execution(blender3, Execution.Context.createContextStub()).search(q);
        assertEquals(9, br3.getHitCount());
        assertEquals(67, br3.getTotalHitCount());
        assertEquals("http://host6.com/", br3.hits().get(0).getId().toString());

        q.setWindow(0, 10);
        Result br4 = new Execution(blender3, Execution.Context.createContextStub()).search(q);
        assertEquals(9, br4.getHitCount());
        assertEquals("http://host6.com/", br4.hits().get(0).getId().toString());

        q.setWindow(3, 10);
        Result br5 = new Execution(blender3, Execution.Context.createContextStub()).search(q);
        assertEquals(6, br5.getHitCount());
        assertEquals("http://host3.com/", br5.hits().get(0).getId().toString());

        q.setWindow(3, 10);
        br5 = new Execution(blender3, Execution.Context.createContextStub()).search(q);
        assertEquals(6, br5.getHitCount());
        assertEquals("http://host3.com/", br5.hits().get(0).getId().toString());

        q.setWindow(3, 10);
        br5 = new Execution(blender3, Execution.Context.createContextStub()).search(q);
        assertEquals(6, br5.getHitCount());
        assertEquals("http://host3.com/", br5.hits().get(0).getId().toString());

    }

    @Test
    void testMultipleBackendsWithDuplicateRemoval() {
        DocumentSourceSearcher docSource1 = new DocumentSourceSearcher();
        DocumentSourceSearcher docSource2 = new DocumentSourceSearcher();
        Query q = new Query("/search?query=hannibal&search=a,b");
        Result r1 = new Result(q);
        Result r2 = new Result(q);

        r1.setTotalHitCount(1);
        r1.hits().add(new FastHit("http://host1.com/", 101));
        docSource1.addResult(q, r1);
        r2.hits().add(new FastHit("http://host1.com/", 102));
        r2.setTotalHitCount(1);
        docSource2.addResult(q, r2);

        BlendingSearcherWrapper blender = new BlendingSearcherWrapper("[id]");
        blender.addSource(new Chain<>("a", new FillSearcher(), docSource1));
        blender.addSource(new Chain<>("b", new FillSearcher(), docSource2));
        blender.initialize();
        q.setWindow(0, 10);
        Result cr = new Execution(blender, Execution.Context.createContextStub()).search(q);
        assertEquals(1, cr.getHitCount());
        assertEquals(101, ((int) cr.hits().get(0).getRelevance().getScore()));
    }

    @Test
    void testMultipleBackendsWithErrorMerging() {
        DocumentSourceSearcher docSource1 = new DocumentSourceSearcher();
        DocumentSourceSearcher docSource2 = new DocumentSourceSearcher();
        Query q = new Query("/search?query=hannibal&search=a,b");
        Result r1 = new Result(q, ErrorMessage.createNoBackendsInService(null));
        Result r2 = new Result(q, ErrorMessage.createRequestTooLarge(null));

        r1.setTotalHitCount(0);
        docSource1.addResult(q, r1);
        r2.hits().add(new FastHit("http://host1.com/", 102));
        r2.setTotalHitCount(1);
        docSource2.addResult(q, r2);

        BlendingSearcherWrapper blender = new BlendingSearcherWrapper();
        blender.addSource(new Chain<>("a", new FillSearcher(), docSource1));
        blender.addSource(new Chain<>("b", new FillSearcher(), docSource2));
        blender.initialize();
        q.setWindow(0, 10);
        Result cr = new Execution(blender, Execution.Context.createContextStub()).search(q);
        assertEquals(2, cr.getHitCount());
        assertEquals(1, cr.getConcreteHitCount());
        com.yahoo.search.result.ErrorHit errorHit = cr.hits().getErrorHit();
        Iterator errorIterator = errorHit.errorIterator();
        List<String> errorList = Arrays.asList("Source 'a': No backends in service. Try later",
                "Source 'b': 2: Request too large");
        String a = errorIterator.next().toString();
        assertTrue(errorList.contains(a), a);
        String b = errorIterator.next().toString();
        assertTrue(errorList.contains(b), a);
        assertFalse(errorIterator.hasNext());
        assertEquals(102, ((int) cr.hits().get(1).getRelevance().getScore()));
        assertEquals(com.yahoo.container.protect.Error.NO_BACKENDS_IN_SERVICE.code, cr.hits().getError().getCode());
    }

    @Test
    void testBlendingWithSortSpec() {
        DocumentSourceSearcher docSource1 = new DocumentSourceSearcher();
        DocumentSourceSearcher docSource2 = new DocumentSourceSearcher();

        Query q = new Query("/search?query=banana+&sorting=%2Bfoobar");

        Result r1 = new Result(q);
        Result r2 = new Result(q);

        r1.setTotalHitCount(3);
        Hit r1h1 = new Hit("http://host1.com/relevancy101", 101);
        r1h1.setField("foobar", "3");
        r1h1.setQuery(q);
        Hit r1h2 = new Hit("http://host2.com/relevancy102", 102);
        r1h2.setField("foobar", "6");
        r1h2.setQuery(q);
        Hit r1h3 = new Hit("http://host3.com/relevancy103", 103);
        r1h3.setField("foobar", "2");
        r1h3.setQuery(q);
        r1.hits().add(r1h1);
        r1.hits().add(r1h2);
        r1.hits().add(r1h3);
        docSource1.addResult(q, r1);

        r2.setTotalHitCount(3);
        Hit r2h1 = new Hit("http://host1.com/relevancy201", 201);
        r2h1.setField("foobar", "5");
        r2h1.setQuery(q);
        Hit r2h2 = new Hit("http://host2.com/relevancy202", 202);
        r2h2.setField("foobar", "1");
        r2h2.setQuery(q);
        Hit r2h3 = new Hit("http://host3.com/relevancy203", 203);
        r2h3.setField("foobar", "4");
        r2h3.setQuery(q);
        r2.hits().add(r2h1);
        r2.hits().add(r2h2);
        r2.hits().add(r2h3);
        docSource2.addResult(q, r2);

        BlendingSearcherWrapper blender = new BlendingSearcherWrapper();
        blender.addSource(new Chain<>("a", new FillSearcher(), docSource1));
        blender.addSource(new Chain<>("b", new FillSearcher(), docSource2));
        blender.initialize();
        q.setWindow(0, 10);
        Result br = new Execution(blender, Execution.Context.createContextStub()).search(q);
        assertEquals(202, ((int) br.hits().get(0).getRelevance().getScore()));
        assertEquals(103, ((int) br.hits().get(1).getRelevance().getScore()));
        assertEquals(101, ((int) br.hits().get(2).getRelevance().getScore()));
        assertEquals(203, ((int) br.hits().get(3).getRelevance().getScore()));
        assertEquals(201, ((int) br.hits().get(4).getRelevance().getScore()));
        assertEquals(102, ((int) br.hits().get(5).getRelevance().getScore()));
    }

    /**
     * Disabled because the document source searcher does not handle being asked for
     * document sumaries for hits it did not create (it will insert the wrong values).
     * But are we sure fsearch handles this case correctly?
     */
    @Test
    void testBlendingWithSortSpecAnd2Phase() {
        DocumentSourceSearcher docSource1 = new DocumentSourceSearcher();
        DocumentSourceSearcher docSource2 = new DocumentSourceSearcher();

        Query q = new Query("/search?query=banana+&sorting=%2Battributefoobar");
        Result r1 = new Result(q);
        Result r2 = new Result(q);

        r1.setTotalHitCount(3);
        Hit r1h1 = new Hit("http://host1.com/relevancy101", 101);
        r1h1.setField("attributefoobar", "3");
        Hit r1h2 = new Hit("http://host2.com/relevancy102", 102);
        r1h2.setField("attributefoobar", "6");
        Hit r1h3 = new Hit("http://host3.com/relevancy103", 103);
        r1h3.setField("attributefoobar", "2");
        r1.hits().add(r1h1);
        r1.hits().add(r1h2);
        r1.hits().add(r1h3);
        docSource1.addResult(q, r1);

        r2.setTotalHitCount(3);
        Hit r2h1 = new Hit("http://host1.com/relevancy201", 201);
        r2h1.setField("attributefoobar", "5");
        Hit r2h2 = new Hit("http://host2.com/relevancy202", 202);
        r2h2.setField("attributefoobar", "1");
        Hit r2h3 = new Hit("http://host3.com/relevancy203", 203);
        r2h3.setField("attributefoobar", "4");
        r2.hits().add(r2h1);
        r2.hits().add(r2h2);
        r2.hits().add(r2h3);
        docSource2.addResult(q, r2);

        BlendingSearcherWrapper blender = new BlendingSearcherWrapper();
        blender.addSource("chainedone", docSource1);
        blender.addSource("chainedtwo", docSource2);
        blender.initialize();
        q.setWindow(0, 10);
        Result br = new Execution(blender, Execution.Context.createContextStub()).search(q);
        assertEquals(202, ((int) br.hits().get(0).getRelevance().getScore()));
        assertEquals(103, ((int) br.hits().get(1).getRelevance().getScore()));
        assertEquals(101, ((int) br.hits().get(2).getRelevance().getScore()));
        assertEquals(203, ((int) br.hits().get(3).getRelevance().getScore()));
        assertEquals(201, ((int) br.hits().get(4).getRelevance().getScore()));
        assertEquals(102, ((int) br.hits().get(5).getRelevance().getScore()));
    }

    private BlendingSearcherWrapper setupFirstAndSecond() {
        DocumentSourceSearcher first = new DocumentSourceSearcher();
        DocumentSourceSearcher second = new DocumentSourceSearcher();

        Query query = new Query("?query=banana");

        Result r1 = new Result(query);
        r1.setTotalHitCount(1);
        Hit r1h1 = new Hit("http://first/relevancy100", 200);
        r1.hits().add(r1h1);
        first.addResult(query, r1);

        Result r2 = new Result(query);
        r2.setTotalHitCount(2);
        Hit r2h1 = new Hit("http://second/relevancy300", 300);
        Hit r2h2 = new Hit("http://second/relevancy100", 100);
        r2.hits().add(r2h1);
        r2.hits().add(r2h2);
        second.addResult(query, r2);

        BlendingSearcherWrapper blender = new BlendingSearcherWrapper();
        blender.addSource(new Chain<>("first", new FillSearcher(), first));
        blender.addSource(new Chain<>("second", new FillSearcher(), second));
        blender.initialize();
        return blender;
    }

    @Test
    void testOnlyFirstBackend() {
        BlendingSearcherWrapper searcher = setupFirstAndSecond();
        Query query = new Query("/search?query=banana&search=first");

        Result result = new Execution(searcher, Execution.Context.createContextStub()).search(query);
        assertEquals(1, result.getHitCount());
        assertEquals(200.0, result.hits().get(0).getRelevance().getScore(), delta);
    }

    @Test
    void testOnlySecondBackend() {
        BlendingSearcherWrapper searcher = setupFirstAndSecond();
        Query query = new Query("/search?query=banana&search=second");

        Result result = new Execution(searcher, Execution.Context.createContextStub()).search(query);
        assertEquals(2, result.getHitCount());
        assertEquals(300.0, result.hits().get(0).getRelevance().getScore(), delta);
        assertEquals(100.0, result.hits().get(1).getRelevance().getScore(), delta);
    }

    @Test
    void testBothBackendsExplicitly() {
        BlendingSearcherWrapper searcher = setupFirstAndSecond();
        Query query = new Query("/search?query=banana&search=first,second");

        Result result = new Execution(searcher, Execution.Context.createContextStub()).search(query);
        assertEquals(3, result.getHitCount());
        assertEquals(300.0, result.hits().get(0).getRelevance().getScore(), delta);
        assertEquals(200.0, result.hits().get(1).getRelevance().getScore(), delta);
        assertEquals(100.0, result.hits().get(2).getRelevance().getScore(), delta);
    }

    @Test
    void testBothBackendsImplicitly() {
        BlendingSearcherWrapper searcher = setupFirstAndSecond();
        Query query = new Query("/search?query=banana");

        Result result = new Execution(searcher, Execution.Context.createContextStub()).search(query);
        assertEquals(3, result.getHitCount());
        assertEquals(300.0, result.hits().get(0).getRelevance().getScore(), delta);
        assertEquals(200.0, result.hits().get(1).getRelevance().getScore(), delta);
        assertEquals(100.0, result.hits().get(2).getRelevance().getScore(), delta);
    }

    @Test
    void testNonexistingBackendCausesError() {
        BlendingSearcherWrapper searcher = setupFirstAndSecond();
        Query query = new Query("/search?query=banana&search=nonesuch");

        Result result = new Execution(searcher, Execution.Context.createContextStub()).search(query);
        assertEquals(0, result.getConcreteHitCount());
        assertNotNull(result.hits().getError());
        ErrorMessage e = result.hits().getError();
        assertEquals("Invalid query parameter", e.getMessage());
        //assertEquals("No source named 'nonesuch' to search. Valid sources are [first, second]",
        //             e.getDetailedMessage());
    }

    @Test
    void testNonexistingBackendsCausesErrorOnFirst() {
        // Feel free to change to include all in the detail message...
        BlendingSearcherWrapper searcher = setupFirstAndSecond();
        Query query = new Query("/search?query=banana&search=nonesuch,orsuch");

        Result result = new Execution(searcher, Execution.Context.createContextStub()).search(query);
        assertEquals(0, result.getConcreteHitCount());
        assertNotNull(result.hits().getError());
        ErrorMessage e = result.hits().getError();
        assertEquals("Invalid query parameter", e.getMessage());
        //TODO: Do not depend on sources order
        assertEquals("4: Invalid query parameter: Could not resolve source ref 'nonesuch'. Could not resolve source ref 'orsuch'. Valid source refs are first, second.",
                e.toString());
    }

    @Test
    void testExistingAndNonExistingBackendCausesBothErrorAndResult() {
        BlendingSearcherWrapper searcher = setupFirstAndSecond();
        Query query = new Query("/search?query=banana&search=first,nonesuch,second");

        Result result = new Execution(searcher, Execution.Context.createContextStub()).search(query);
        assertEquals(3, result.getConcreteHitCount());
        assertEquals(300.0, result.hits().get(1).getRelevance().getScore(), delta);
        assertEquals(200.0, result.hits().get(2).getRelevance().getScore(), delta);
        assertEquals(100.0, result.hits().get(3).getRelevance().getScore(), delta);
        assertNotNull(result.hits().getError());
        ErrorMessage e = result.hits().getError();
        //TODO: Do not depend on sources order
        assertEquals("Could not resolve source ref 'nonesuch'. Valid source refs are first, second.",
                e.getDetailedMessage());
    }

    @Test
    void testBlendingFederationWithGrouping() {
        DocumentSourceSearcher docSource1 = new DocumentSourceSearcher();
        DocumentSourceSearcher docSource2 = new DocumentSourceSearcher();

        Query q = new Query("/search?query=test");

        Result r1 = new Result(q);
        Result r2 = new Result(q);

        RootGroup root1 = new RootGroup(0, null);
        Group subGroup11 = new Group(new StringBucketId("a", "b"), new Relevance(1.0));
        subGroup11.add(new Group(new StringId("unique1"), new Relevance(1.0)));
        subGroup11.add(new Group(new StringId("unique5"), new Relevance(1.0)));
        subGroup11.add(new Group(new StringId("unique6"), new Relevance(1.0)));
        subGroup11.add(new Group(new StringId("unique7"), new Relevance(1.0)));
        root1.add(subGroup11);
        r1.hits().add(root1);
        docSource1.addResult(q, r1);

        RootGroup root2 = new RootGroup(0, null);
        Group subGroup21 = new Group(new StringBucketId("a", "b"), new Relevance(1.0));
        subGroup21.add(new Group(new StringId("unique1"), new Relevance(1.0)));
        subGroup21.add(new Group(new StringId("unique2"), new Relevance(1.0)));
        subGroup21.add(new Group(new StringId("unique3"), new Relevance(1.0)));
        root2.add(subGroup21);
        Group subGroup22 = new Group(new StringBucketId("c", "d"), new Relevance(1.0));
        subGroup22.add(new Group(new StringId("unique3"), new Relevance(1.0)));
        subGroup22.add(new Group(new StringId("unique4"), new Relevance(1.0)));
        root2.add(subGroup22);
        r2.hits().add(root2);
        docSource2.addResult(q, r2);

        BlendingSearcherWrapper blender = new BlendingSearcherWrapper();
        blender.addSource(new Chain<>("a", new FillSearcher(), docSource1));
        blender.addSource(new Chain<>("b", new FillSearcher(), docSource2));
        blender.initialize();
        q.setWindow(0, 10);
        Result result = new Execution(blender, Execution.Context.createContextStub()).search(q);
        assertEquals(2, result.hits().size());

        assertTrue(result.hits().get(0) instanceof RootGroup);
        RootGroup resultRoot1 = (RootGroup) result.hits().get(0);
        assertEquals(1, resultRoot1.asList().size());

        assertTrue(result.hits().get(1) instanceof RootGroup);
        RootGroup resultRoot2 = (RootGroup) result.hits().get(1);
        assertEquals(2, resultRoot2.asList().size());
    }

    /** Multiple document types in the same cluster are returned without a top level group representing each */
    @Test
    void testBlendingMultipleDocumentTypesWithGrouping() {
        DocumentSourceSearcher docSource = new DocumentSourceSearcher();

        Query q = new Query("/search?query=test");

        Result r = new Result(q);

        RootGroup root1 = new RootGroup(0, null);
        Group subGroup11 = new Group(new StringBucketId("a", "b"), new Relevance(1.0));
        subGroup11.add(new Group(new StringId("unique1"), new Relevance(1.0)));
        subGroup11.add(new Group(new StringId("unique5"), new Relevance(1.0)));
        subGroup11.add(new Group(new StringId("unique6"), new Relevance(1.0)));
        subGroup11.add(new Group(new StringId("unique7"), new Relevance(1.0)));
        root1.add(subGroup11);
        r.hits().add(root1);

        RootGroup root2 = new RootGroup(0, null);
        Group subGroup21 = new Group(new StringBucketId("a", "b"), new Relevance(1.0));
        subGroup21.add(new Group(new StringId("unique1"), new Relevance(1.0)));
        subGroup21.add(new Group(new StringId("unique2"), new Relevance(1.0)));
        subGroup21.add(new Group(new StringId("unique3"), new Relevance(1.0)));
        root2.add(subGroup21);
        Group subGroup22 = new Group(new StringBucketId("c", "d"), new Relevance(1.0));
        subGroup22.add(new Group(new StringId("unique3"), new Relevance(1.0)));
        subGroup22.add(new Group(new StringId("unique4"), new Relevance(1.0)));
        root2.add(subGroup22);
        r.hits().add(root2);

        docSource.addResult(q, r);

        Chain<Searcher> chain = new Chain<>("main",
                new FillSearcher(),
                new BlendingSearcher(ComponentId.fromString("test"), new QrSearchersConfig.Builder().build()),
                docSource);
        q.setWindow(0, 10);
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(q);
        assertEquals(3, result.hits().size());

        assertTrue(result.hits().get(0) instanceof Group);
        Group resultSubGroup1 = (Group) result.hits().get(0);
        assertEquals(4, resultSubGroup1.asList().size());

        assertTrue(result.hits().get(1) instanceof Group);
        Group resultSubGroup2 = (Group) result.hits().get(1);
        assertEquals(3, resultSubGroup2.asList().size());

        assertTrue(result.hits().get(2) instanceof Group);
        Group resultSubGroup3 = (Group) result.hits().get(2);
        assertEquals(2, resultSubGroup3.asList().size());
    }

}
