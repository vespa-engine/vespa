// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.chain.Chain;
import com.yahoo.io.IOUtils;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.prelude.templates.SearchRendererAdaptor;
import com.yahoo.prelude.templates.TiledTemplateSet;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.federation.http.HTTPProviderSearcher;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests representing a federated and grouped result as a Result object and
 * rendering a tiled output of the result
 *
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class TilingTestCase {

    /**
     * This result contains two blocks (center and right).
     * The center block contains multiple subblocks while the right one contains a single block of ads.
     */
    @Test
    public void testTiling() throws IOException {
        Chain<Searcher> chain=new Chain<>("tiling", new TiledResultProducer());

        // Query it
        Query query = new Query("/tiled?query=foo");
        Result result = callSearchAndSetRenderer(chain, query);
        assertRendered(IOUtils.readFile(new File("src/test/java/com/yahoo/prelude/templates/test/tilingexample.xml")),result);
    }

    /**
     * This result contains center section and meta blocks.
     */
    @Test
    public void testTiling2() throws IOException {
        Chain<Searcher> chain= new Chain<>("tiling", new TiledResultProducer2());

        // Query it
        Query query=new Query("/tiled?query=foo");
        Result result= callSearchAndSetRenderer(chain, query);
        assertRendered(IOUtils.readFile(new File("src/test/java/com/yahoo/prelude/templates/test/tilingexample2.xml")),result);
    }

    private Result callSearchAndSetRenderer(Chain<Searcher> chain, Query query) {
        Execution.Context context = new Execution.Context(null, null, null, new RendererRegistry(MoreExecutors.directExecutor()), new SimpleLinguistics());
        Result result = new Execution(chain, context).search(query);
        result.getTemplating().setRenderer(new SearchRendererAdaptor(new TiledTemplateSet()));
        return result;
    }

    public static void assertRenderedStartsWith(String expected,Result result) throws IOException {
        assertRendered(expected,result,false);
    }

    public static void assertRendered(String expected,Result result) throws IOException {
        assertRendered(expected,result,true);
    }

    public static void assertRendered(String expected, Result result,boolean checkFullEquality) throws IOException {
        if (checkFullEquality)
            assertEquals(filterComments(expected), getRendered(result));
        else
            assertTrue(getRendered(result).startsWith(expected));
    }

    private static String filterComments(String s) {
        StringBuilder b = new StringBuilder();
        for (String line : s.split("\\\n"))
            if ( ! line.startsWith("<!--"))
                b.append(line).append("\n");
        return b.toString();
    }

    public static String getRendered(Result result) throws IOException {
        if (result.getTemplating().getRenderer() == null)
            result.getTemplating().setTemplates(null);

        // Done in a roundabout way to simulate production code path
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Charset cs = Charset.forName(result.getTemplating().getRenderer().getEncoding());
        CharsetDecoder decoder = cs.newDecoder();
        SearchRendererAdaptor.callRender(stream, result);
        stream.flush();
        return decoder.decode(ByteBuffer.wrap(stream.toByteArray())).toString();
    }

    private static class TiledResultProducer extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Result result = new Result(query);
            result.setTotalHitCount(2800000000l);

            // Blocks
            HitGroup centerBlock=(HitGroup)result.hits().add(new HitGroup("section:center"));
            centerBlock.types().add("section");
            centerBlock.setField("region","center");

            HitGroup rightBlock=(HitGroup)result.hits().add(new HitGroup("section:right"));
            rightBlock.types().add("section");
            rightBlock.setField("region","right");

            // Center groups
            HitGroup navigation=(HitGroup)centerBlock.add(new HitGroup("group:navigation",1.0));
            navigation.types().add("group");
            navigation.setField("type","navigation");

            HitGroup adsNorth=(HitGroup)centerBlock.add(new HitGroup("group:ads:north",0.9));
            adsNorth.types().add("group");
            adsNorth.setField("type","ads");

            HitGroup hits=(HitGroup)centerBlock.add(new HitGroup("group:navigation",0.8));
            hits.types().add("group");
            hits.setField("type","hits");

            HitGroup adsSouth=(HitGroup)centerBlock.add(new HitGroup("group:ads:south",0.7));
            adsSouth.types().add("group");
            adsSouth.setField("type","ads");

            // Right group
            HitGroup adsRight=(HitGroup)rightBlock.add(new HitGroup("group:ads:right",0.7));
            adsRight.types().add("group");
            adsRight.setField("type","ads");

            // Navigation content
            /*
            Hit alsoTry=navigation.add(new Hit("alsotry"));
            alsoTry.types().add("alsotry");
            alsoTry.setMeta(true);
            LinkList links=new LinkList();
            links.add(new Link("Hilton","?search=Hilton hotel"));
            links.add(new Link("Habbo hotel","?search=Habbo hotel"));
            links.add(new Link("Marriott","?search=Marriott hotel"));
            alsoTry.setField("links",links);
            */

            // North ads content
            Hit ad1=adsNorth.add(new Hit("http://www.hotels.com",0.7));
            ad1.types().add("ad");
            ad1.setAuxiliary(true);
            ad1.setField("title",new XMLString("Cheap <hi>hotels</hi>"));
            ad1.setField("body",new XMLString("Low Rates Guaranteed. Call a <hi>Hotel</hi> Expert."));

            Hit ad2=adsNorth.add(new Hit("http://www.expedia.com",0.6));
            ad2.types().add("ad");
            ad2.setAuxiliary(true);
            ad2.setField("title",new XMLString("Cheap <hi>hotels</hi> at Expedia"));
            ad2.setField("body","Expedia Special Rates Means We Guarantee Our Low Rates on Rooms.");

//            // Hits content
//            // - news hit
//            HitGroup news1=(HitGroup)hits.add(new HitGroup("newsarticles",0.9));
//            news1.setMeta(false);
//            news1.types().add("news");
//            news1.setField("title","Hotel - News results");
//            Hit article1=news1.add(new Hit("www.miamiherald.com/?article=jhsgd7323",0.5));
//            article1.setAuxiliary(true);
//            article1.setField("title","Celebrity blackout: The Hilton of Paris changes name to regain search traffic");
//            article1.types().add("newsarticle");
//            article1.setField("age",23);
//            article1.setField("source","Miami Herald");
//            Hit article2=news1.add(new Hit("www.sfgate.com/?article=8763khj7",0.4));
//            article2.setAuxiliary(true);
//            article2.setField("title","Hotels - more expensive than staying at home");
//            article2.types().add("newsarticle");
//            article2.setField("age",3500);
//            article2.setField("source","SF Gate");

            // - collapsed hit
            Hit hit1=hits.add(new Hit("www.hotels.com",0.8));
            hit1.types().add("hit.collapsed");
            hit1.setField("title","Hotels.com | Cheap Hotels | Discount Hotel Rooms | Motels | Lodging");
            hit1.setField("body",new XMLString("Hotels.com helps you find great rates on hotels and discount <hi>hotel</hi> packages."));
            /*
            LinkList collapsed1=new LinkList();
            collapsed1.add(new Link("Last Minute Deals","www.hotels.com/lastminutedeals"));
            collapsed1.add(new Link("Hotel Savings","www.hotels.com/deals"));
            collapsed1.add(new Link("800-994-6835","www.hotels.com/?PSRC=OT2"));
            hit1.setField("links",collapsed1);
            */

            // regular hit with links
            Hit hit2=hits.add(new Hit("www.indigohotels.com",0.7));
            hit2.types().add("hit");
            hit2.setField("title","Hotel Indigo Hotels United States - Official Web Site");
            hit2.setField("body","Make Hotel Indigo online hotel reservations and book your hotel rooms today.");
            /*
            LinkList collapsed2=new LinkList();
            collapsed2.add(new Link("800-333-6835","www.indigohotels.com/order"));
            collapsed2.add(new Link("Reservations","www.indigohotels.com/reservations"));
            hit2.setField("links",collapsed2);
            */

            // boring old hit
            Hit hit3=hits.add(new Hit("www.all-hotels.com",0.6));
            hit3.types().add("hit");
            hit3.setField("title","All hotels");
            hit3.setField("body","Online hotel directory and reservations.");

            // South ads
            Hit southAd1=adsSouth.add(new Hit("www.daysinn.com",1.0));
            southAd1.types().add("ad");
            southAd1.setAuxiliary(true);
            southAd1.setField("title","Days Inn Special Deal");
            southAd1.setField("body","Buy now and Save 15% Off Our Best Available Rate with Days Inn.");
            Hit southAd2=adsSouth.add(new Hit("http://www.expedia.com",0.9));
            southAd2.types().add("ad");
            southAd2.setAuxiliary(true);
            southAd2.setField("title",new XMLString("Cheap <hi>hotels</hi> at Expedia"));
            southAd2.setField("body","Expedia Special Rates Means We Guarantee Our Low Rates on Rooms.");

            // Right ads
            Hit rightAd1=adsRight.add(new Hit("www.daysinn.com",1.0));
            rightAd1.types().add("ad");
            rightAd1.setAuxiliary(true);
            rightAd1.setField("title","Days Inn Special Deal");
            rightAd1.setField("body","Buy now and Save 15% Off Our Best Available Rate with Days Inn.");
            Hit rightAd2=adsRight.add(new Hit("www.holidayinn.com",0.9));
            rightAd2.types().add("ad");
            rightAd2.setAuxiliary(true);
            rightAd2.setField("title","Holiday Inn: Official Site");
            rightAd2.setField("body","Book with Holiday Inn. Free Internet. Kids eat free.");

            // Done creating result - must analyze because we add ads then later set them as auxiliary
            result.analyzeHits();

            return result;
        }

    }

    private static class TiledResultProducer2 extends Searcher {

        @Override
        public Result search(Query query,Execution execution) {
            Result result=new Result(query);
            result.setTotalHitCount(1);

    		HitGroup section = new HitGroup("section:center");
            result.hits().add(section);
    		section.setMeta(false);
    		section.types().add("section");
    		section.setField("region", "center");

    		HitGroup yst = new HitGroup("yst");
    		section.add(yst);
    		yst.setMeta(false);
    		yst.setSource("sr");
    		yst.types().add("sr");
    		yst.setField("provider", "yst");

    		Hit theHit = new Hit("159");
    		yst.add(theHit);
    		theHit.setSource("sr");
    		theHit.types().add("sr");
    		theHit.setField("provider", "yst");
    		theHit.setField("title", "Yahoo");

    		HitGroup meta = new HitGroup("meta");
    		result.hits().add(meta);
    		meta.types().add("meta");

    		Hit log = new Hit("com.yahoo.search.federation.yst.YSTBackendSearcherproxy-tw1cache.idp.inktomisearch.com55556/search");
            meta.add(log);
            log.setMeta(true);
    		log.setSource("sr");
    		log.setField("provider", "yst");
            log.types().add("logging");
            log.setField(HTTPProviderSearcher.LOG_URI, "http://proxy-tw1cache.idp.inktomisearch.com:55556/search?qp=yahootw-twp&Fields=url%2Credirecturl%2Cdate%2Csize%2Cformat%2Csms_product%2Ccacheurl%2Cnodename%2Cid%2Clanguage%2Crsslinks%2Crssvalidatedlinks%2Ccpc%2Cclustertype%2Cxml.active_abstract%2Cactive_abstract_type%2Cactive_abstract_source%2Ccontract_id%2Ctranslated%2Cxml.ydir_tw_hotlist_data%2Cxml.summary%2Cclustercollision%2Cxml.pi_info%2Cpage_adult_overridable%2Cpage_spam_overridable%2Ccategory_ydir%2Chate_edb&Unique=doc%2Chost+2&QueryEncoding=utf-8&Query=ALLWORDS%28yahoo%29&Database=dewownrm-zh-tw&FirstResult=0&srcpvid=&cacheecho=1&ResultsEncoding=utf-8&QueryLanguage=Chinese-traditional&Region=US&NumResults=10&Client=yahoous2");
            log.setField(HTTPProviderSearcher.LOG_SCHEME, "http");
            log.setField(HTTPProviderSearcher.LOG_HOST, "proxy-tw1cache.idp.inktomisearch.com");
            log.setField(HTTPProviderSearcher.LOG_PORT, "55556");
            log.setField(HTTPProviderSearcher.LOG_PATH, "/search");
            log.setField(HTTPProviderSearcher.LOG_STATUS, "200");
            log.setField(HTTPProviderSearcher.LOG_LATENCY_CONNECT, "757");
            log.setField(HTTPProviderSearcher.LOG_RESPONSE_HEADER_PREFIX + "content-length", "16217");

    		result.analyzeHits();

            return result;
        }

    }

}
