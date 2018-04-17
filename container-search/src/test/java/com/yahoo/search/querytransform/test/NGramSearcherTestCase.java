// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.yahoo.component.chain.Chain;
import com.yahoo.language.Language;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.querytransform.CJKSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.Parser;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import com.yahoo.search.querytransform.NGramSearcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import org.junit.Before;
import org.junit.Test;

import static com.yahoo.search.searchchain.Execution.Context.createContextStub;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class NGramSearcherTestCase {

    private Searcher searcher;
    private IndexFacts indexFacts;

    @Before
    public void setUp() {
        searcher=new NGramSearcher(new SimpleLinguistics());
        indexFacts=new IndexFacts();

        Index defaultIndex=new Index("default");
        defaultIndex.setNGram(true,3);
        defaultIndex.setDynamicSummary(true);
        indexFacts.addIndex("default",defaultIndex);

        Index test=new Index("test");
        test.setHighlightSummary(true);
        indexFacts.addIndex("default",test);

        Index gram2=new Index("gram2");
        gram2.setNGram(true,2);
        gram2.setDynamicSummary(true);
        indexFacts.addIndex("default",gram2);

        Index gram3=new Index("gram3");
        gram3.setNGram(true,3);
        gram3.setHighlightSummary(true);
        indexFacts.addIndex("default",gram3);

        Index gram14=new Index("gram14");
        gram14.setNGram(true,14);
        gram14.setDynamicSummary(true);
        indexFacts.addIndex("default",gram14);
    }

    private IndexFacts getMixedSetup() {
        IndexFacts indexFacts = new IndexFacts();
        String musicDoctype = "music";
        String songDoctype = "song";
        Index musicDefault = new Index("default");
        musicDefault.setNGram(true, 1);
        indexFacts.addIndex(musicDoctype, musicDefault);
        Index songDefault = new Index("default");
        indexFacts.addIndex(songDoctype, songDefault);
        Map<String, List<String>> clusters = new HashMap<>();
        clusters.put("musicOnly", Arrays.asList(new String[] { musicDoctype }));
        clusters.put("songOnly", Arrays.asList(new String[] { songDoctype }));
        clusters.put("musicAndSong", Arrays.asList(new String[] { musicDoctype, songDoctype }));
        indexFacts.setClusters(clusters);
        return indexFacts;
    }

    @Test
    public void testMixedDocTypes() {
        final IndexFacts mixedSetup = getMixedSetup();
        {
            Query q = new Query("?query=abc&restrict=song");
            new Execution(searcher, Execution.Context.createContextStub(mixedSetup)).search(q);
            assertEquals("abc", q.getModel().getQueryTree().toString());
        }
        {
            Query q = new Query("?query=abc&restrict=music");
            new Execution(searcher, Execution.Context.createContextStub(mixedSetup)).search(q);
            assertEquals("AND a b c", q.getModel().getQueryTree().toString());
        }
        {
            Query q = new Query("?query=abc");
            new Execution(searcher, Execution.Context.createContextStub(mixedSetup)).search(q);
            assertEquals("AND a b c", q.getModel().getQueryTree().toString());
        }
        {
            Query q = new Query("?query=abc&search=song");
            new Execution(searcher, Execution.Context.createContextStub(mixedSetup)).search(q);
            assertEquals("abc", q.getModel().getQueryTree().toString());
        }
        {
            Query q = new Query("?query=abc&search=music");
            new Execution(searcher, Execution.Context.createContextStub(mixedSetup)).search(q);
            assertEquals("AND a b c", q.getModel().getQueryTree().toString());
        }
    }

    @Test
    public void testMixedClusters() {
        final IndexFacts mixedSetup = getMixedSetup();
        {
            Query q = new Query("?query=abc&search=songOnly");
            new Execution(searcher, Execution.Context.createContextStub(mixedSetup)).search(q);
            assertEquals("abc", q.getModel().getQueryTree().toString());
        }
        {
            Query q = new Query("?query=abc&search=musicOnly");
            new Execution(searcher, Execution.Context.createContextStub(mixedSetup)).search(q);
            assertEquals("AND a b c", q.getModel().getQueryTree().toString());
        }
        {
            Query q = new Query("?query=abc&search=musicAndSong&restrict=music");
            new Execution(searcher, Execution.Context.createContextStub(mixedSetup)).search(q);
            assertEquals("AND a b c", q.getModel().getQueryTree().toString());
        }
        {
            Query q = new Query("?query=abc&search=musicAndSong&restrict=song");
            new Execution(searcher, Execution.Context.createContextStub(mixedSetup)).search(q);
            assertEquals("abc", q.getModel().getQueryTree().toString());
        }
    }

    @Test
    public void testClusterMappingWithMixedDoctypes() {
        final IndexFacts mixedSetup = getMixedSetup();

    }

    @Test
    public void testNGramRewritingMixedQuery() {
        Query q=new Query("?query=foo+gram3:engul+test:bar");
        new Execution(searcher,Execution.Context.createContextStub(indexFacts)).search(q);
        assertEquals("AND foo (AND gram3:eng gram3:ngu gram3:gul) test:bar",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testNGramRewritingNGramOnly() {
        Query q=new Query("?query=gram3:engul");
        new Execution(searcher,Execution.Context.createContextStub(indexFacts)).search(q);
        assertEquals("AND gram3:eng gram3:ngu gram3:gul",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testNGramRewriting2NGramsOnly() {
        Query q=new Query("?query=gram3:engul+gram2:123");
        new Execution(searcher,Execution.Context.createContextStub(indexFacts)).search(q);
        assertEquals("AND (AND gram3:eng gram3:ngu gram3:gul) (AND gram2:12 gram2:23)",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testNGramRewritingShortOnly() {
        Query q=new Query("?query=gram3:en");
        new Execution(searcher,Execution.Context.createContextStub(indexFacts)).search(q);
        assertEquals("gram3:en",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testNGramRewritingShortInMixes() {
        Query q=new Query("?query=test:a+gram3:en");
        new Execution(searcher,Execution.Context.createContextStub(indexFacts)).search(q);
        assertEquals("AND test:a gram3:en",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testNGramRewritingPhrase() {
        Query q=new Query("?query=gram3:%22engul+a+holi%22");
        new Execution(searcher,Execution.Context.createContextStub(indexFacts)).search(q);
        assertEquals("gram3:\"eng ngu gul a hol oli\"",q.getModel().getQueryTree().toString());
    }

    /**
     * Note that single-term phrases are simplified to just the term at parse time,
     * so the ngram rewriter cannot know to keep the grams as a phrase in this case.
     */
    @Test
    public void testNGramRewritingPhraseSingleTerm() {
        Query q=new Query("?query=gram3:%22engul%22");
        new Execution(searcher,Execution.Context.createContextStub(indexFacts)).search(q);
        assertEquals("AND gram3:eng gram3:ngu gram3:gul",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testNGramRewritingAdditionalTermInfo() {
        Query q=new Query("?query=gram3:engul!50+foo+gram2:123!150");
        new Execution(searcher,Execution.Context.createContextStub(indexFacts)).search(q);
        AndItem root=(AndItem)q.getModel().getQueryTree().getRoot();
        AndItem gram3And=(AndItem)root.getItem(0);
        AndItem gram2And=(AndItem)root.getItem(2);

        assertExtraTermInfo(50,"engul",gram3And.getItem(0));
        assertExtraTermInfo(50,"engul",gram3And.getItem(1));
        assertExtraTermInfo(50,"engul",gram3And.getItem(2));
        assertExtraTermInfo(150,"123",gram2And.getItem(0));
        assertExtraTermInfo(150,"123",gram2And.getItem(1));
    }

    private void assertExtraTermInfo(int weight,String origin, Item g) {
        WordItem gram=(WordItem)g;
        assertEquals(weight,gram.getWeight());
        assertEquals(origin,gram.getOrigin().getValue());
        assertTrue(gram.isProtected());
        assertFalse(gram.isFromQuery());
    }

    @Test
    public void testNGramRewritingExplicitDefault() {
        Query q=new Query("?query=default:engul");
        new Execution(searcher,Execution.Context.createContextStub(indexFacts)).search(q);
        assertEquals("AND default:eng default:ngu default:gul",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testNGramRewritingImplicitDefault() {
        Query q=new Query("?query=engul");
        new Execution(searcher,Execution.Context.createContextStub(indexFacts)).search(q);
        assertEquals("AND eng ngu gul",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testGramsWithSegmentation() {
        assertGramsWithSegmentation(new Chain<>(searcher));
        assertGramsWithSegmentation(new Chain<>(new CJKSearcher(),searcher));
        assertGramsWithSegmentation(new Chain<>(searcher,new CJKSearcher()));
    }

    public void assertGramsWithSegmentation(Chain<Searcher> chain) {
        // "first" "second" and "third" are segments in the "test" language
        Item item = parseQuery("gram14:firstsecondthird", Query.Type.ANY);
        Query q=new Query("?query=ignored");
        q.getModel().setLanguage(Language.UNKNOWN);
        q.getModel().getQueryTree().setRoot(item);
        new Execution(chain,createContextStub(indexFacts)).search(q);
        assertEquals("AND gram14:firstsecondthi gram14:irstsecondthir gram14:rstsecondthird",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testGramsWithSegmentationSingleSegment() {
        assertGramsWithSegmentationSingleSegment(new Chain<>(searcher));
        assertGramsWithSegmentationSingleSegment(new Chain<>(new CJKSearcher(),searcher));
        assertGramsWithSegmentationSingleSegment(new Chain<>(searcher,new CJKSearcher()));
    }

    public void assertGramsWithSegmentationSingleSegment(Chain<Searcher> chain) {
        // "first" "second" and "third" are segments in the "test" language
        Item item = parseQuery("gram14:first", Query.Type.ANY);
        Query q=new Query("?query=ignored");
        q.getModel().setLanguage(Language.UNKNOWN);
        q.getModel().getQueryTree().setRoot(item);
        new Execution(chain,createContextStub(indexFacts)).search(q);
        assertEquals("gram14:first",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testGramsWithSegmentationSubstringSegmented() {
        assertGramsWithSegmentationSubstringSegmented(new Chain<>(searcher));
        assertGramsWithSegmentationSubstringSegmented(new Chain<>(new CJKSearcher(),searcher));
        assertGramsWithSegmentationSubstringSegmented(new Chain<>(searcher,new CJKSearcher()));
    }

    public void assertGramsWithSegmentationSubstringSegmented(Chain<Searcher> chain) {
        // "first" "second" and "third" are segments in the "test" language
        Item item = parseQuery("gram14:afirstsecondthirdo", Query.Type.ANY);
        Query q=new Query("?query=ignored");
        q.getModel().setLanguage(Language.UNKNOWN);
        q.getModel().getQueryTree().setRoot(item);
        new Execution(chain,createContextStub(indexFacts)).search(q);
        assertEquals("AND gram14:afirstsecondth gram14:firstsecondthi gram14:irstsecondthir gram14:rstsecondthird gram14:stsecondthirdo",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testGramsWithSegmentationMixed() {
        assertGramsWithSegmentationMixed(new Chain<>(searcher));
        assertGramsWithSegmentationMixed(new Chain<>(new CJKSearcher(),searcher));
        assertGramsWithSegmentationMixed(new Chain<>(searcher,new CJKSearcher()));
    }

    public void assertGramsWithSegmentationMixed(Chain<Searcher> chain) {
        // "first" "second" and "third" are segments in the "test" language
        Item item = parseQuery("a gram14:afirstsecondthird b gram14:hi", Query.Type.ALL);
        Query q=new Query("?query=ignored");
        q.getModel().setLanguage(Language.UNKNOWN);
        q.getModel().getQueryTree().setRoot(item);
        new Execution(chain,createContextStub(indexFacts)).search(q);
        assertEquals("AND a (AND gram14:afirstsecondth gram14:firstsecondthi gram14:irstsecondthir gram14:rstsecondthird) b gram14:hi",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testGramsWithSegmentationMixedAndPhrases() {
        assertGramsWithSegmentationMixedAndPhrases(new Chain<>(searcher));
        assertGramsWithSegmentationMixedAndPhrases(new Chain<>(new CJKSearcher(),searcher));
        assertGramsWithSegmentationMixedAndPhrases(new Chain<>(searcher,new CJKSearcher()));
    }

    public void assertGramsWithSegmentationMixedAndPhrases(Chain<Searcher> chain) {
        // "first" "second" and "third" are segments in the "test" language
        Item item = parseQuery("a gram14:\"afirstsecondthird b hi\"", Query.Type.ALL);
        Query q=new Query("?query=ignored");
        q.getModel().setLanguage(Language.UNKNOWN);
        q.getModel().getQueryTree().setRoot(item);
        new Execution(chain,createContextStub(indexFacts)).search(q);
        assertEquals("AND a gram14:\"afirstsecondth firstsecondthi irstsecondthir rstsecondthird b hi\"",q.getModel().getQueryTree().toString());
    }

    @Test
    public void testNGramRecombining() {
        Query q=new Query("?query=ignored");
        Result r=new Execution(new Chain<>(searcher,new MockBackend1()),createContextStub(indexFacts)).search(q);
        Hit h1=r.hits().get("hit1");
        assertEquals("Should be untouched,\u001feven if containing \u001f",h1.getField("test").toString());
        assertTrue(h1.getField("test") instanceof String);

        assertEquals("Blue red Ed A",h1.getField("gram2").toString());
        assertTrue(h1.getField("gram2") instanceof XMLString);

        assertEquals("Separators on borders work","Blue red ed a\u001f",h1.getField("gram3").toString());
        assertTrue(h1.getField("gram3") instanceof String);

        Hit h2=r.hits().get("hit2");
        assertEquals("katt  i...morgen",h2.getField("gram3").toString());
        assertTrue(h2.getField("gram3") instanceof JSONString);

        Hit h3=r.hits().get("hit3");
        assertEquals("\u001ffin\u001f \u001fen\u001f \u001fa\u001f",h3.getField("gram2").toString());
        assertEquals("#Logging in #Java is like that \"Judean P\u001fopul\u001far Front\" scene from \"Life of Brian\".",
                     h3.getField("gram3").toString());
    }

    private Item parseQuery(String query, Query.Type type) {
        Parser parser = ParserFactory.newInstance(type, new ParserEnvironment().setIndexFacts(indexFacts));
        return parser.parse(new Parsable().setQuery(query).setLanguage(Language.UNKNOWN)).getRoot();
    }

    private static class MockBackend1 extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Result r=new Result(query);
            HitGroup g=new HitGroup();
            r.hits().add(g);

            Hit h1=new Hit("hit1");
            h1.setField(Hit.SDDOCNAME_FIELD,"default");
            h1.setField("test","Should be untouched,\u001feven if containing \u001f");
            h1.setField("gram2",new XMLString("\uFFF9Bl\uFFFAbl\uFFFBluue reed \uFFF9Ed\uFFFAed\uFFFB \uFFF9A\uFFFAa\uFFFB"));
            h1.setField("gram3","\uFFF9Blu\uFFFAblu\uFFFBlue red ed a\u001f"); // separator on borders should not trip anything
            g.add(h1);

            Hit h2=new Hit("hit2");
            h2.setField(Hit.SDDOCNAME_FIELD,"default");
            h2.setField("gram3",new JSONString("katatt  i...mororgrgegen"));
            r.hits().add(h2);

            // Test bolding
            Hit h3=new Hit("hit3");
            h3.setField(Hit.SDDOCNAME_FIELD,"default");

            // the result of searching for "fin en a"
            h3.setField("gram2","\u001ffi\u001f\u001fin\u001f \u001fen\u001f \u001fa\u001f");

            // the result from Juniper from of bolding the substring "opul":
            h3.setField("gram3","#Logoggggigining in #Javava is likike thahat \"Jududedeaean Pop\u001fopu\u001f\u001fpul\u001fulalar Froronont\" scecenene frorom \"Lifife of Bririaian\".");
            r.hits().add(h3);
            return r;
        }

    }

}
