// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import com.yahoo.vespa.http.client.FeedClient;
import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xml.sax.SAXParseException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class XmlFeedReaderTest {
    private final static String feedResource = "/vespacorpfeed-prod-sample.xml";

    private final static String feedResource2 = "/xml-challenge.xml";
    private final static String feedResource3 = "/xml-challenge2.xml";
    private final static String feedResource4 = "/xml-challenge3.xml";

    private final String updateDocUpdate =
            "<?xml version=\"1.0\"?>\n" +
            "<vespafeed>\n" +
            "<update documentid=\"id:banana:banana::complex\" documenttype=\"banana\">\n" +
            "  <add fieldpath=\"structarr\">\n" +
            "    <item>\n" +
            "      <bytearr>\n" +
            "        <item>30</item>\n" +
            "        <item>55</item>\n" +
            "      </bytearr>\n" +
            "    </item>\n" +
            "  </add>\n" +
            "</update>\n" +
            "</vespafeed>\n";

    @Test
    public void testReadUpdate() throws Exception {
        InputStream stream = new ByteArrayInputStream(updateDocUpdate.getBytes(StandardCharsets.UTF_8));
        AtomicInteger numSent = new AtomicInteger(0);
        FeedClient feedClient = mock(FeedClient.class);
        XmlFeedReader.read(stream, feedClient, numSent);
        assertThat(numSent.get(), is(1));
    }

    private final String updateDocRemove =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "\n" +
            "<vespafeed>\n" +
            "  <remove documentid=\"id:music:music::http://music.yahoo.com/Bob0/BestOf\" />\n" +
            "  <remove documentid=\"id:music:music::http://music.yahoo.com/Bob9/BestOf\" />\n" +
            "</vespafeed>";

    @Test
    public void testReadRemove() throws Exception {
        InputStream stream = new ByteArrayInputStream(updateDocRemove.getBytes(StandardCharsets.UTF_8));
        AtomicInteger numSent = new AtomicInteger(0);
        FeedClient feedClient = mock(FeedClient.class);
        XmlFeedReader.read(stream, feedClient, numSent);
        assertThat(numSent.get(), is(2));
    }

    private final String insertDocOperation = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"+
            "<vespafeed>\n"+
            "\n"+
            "  <document type=\"music\" documentid=\"id:music:music::http://music.yahoo.com/bobdylan/BestOf\">\n"+
            "    <title>Best of Bob Dylan</title>\n"+
            "  </document>\n"+
            "\n"+
            "  <document type=\"music\" documentid=\"id:music:music::http://music.yahoo.com/metallica/BestOf\">\n"+
            "    <title>Best of Metallica</title>\n"+
            "  </document>\n"+
            "</vespafeed>";

    @Test
    public void testInsert() throws Exception {
        InputStream stream = new ByteArrayInputStream(insertDocOperation.getBytes(StandardCharsets.UTF_8));
        AtomicInteger numSent = new AtomicInteger(0);
        FeedClient feedClient = mock(FeedClient.class);
        XmlFeedReader.read(stream, feedClient, numSent);
        assertThat(numSent.get(), is(2));
    }

    private final String badperation = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"+
            "<vespafeed>\n"+
            "  <badtag type=\"music\" documentid=\"id:music:music::http://music.yahoo.com/bobdylan/BestOf\">\n"+
            "    <title>Best of Bob Dylan</title>\n"+
            "  </badtag>\n"+
            "</vespafeed>";

    @Test
    public void testNonDocument() throws Exception {
        InputStream stream = new ByteArrayInputStream(badperation.getBytes(StandardCharsets.UTF_8));
        AtomicInteger numSent = new AtomicInteger(0);
        FeedClient feedClient = mock(FeedClient.class);
        XmlFeedReader.read(stream, feedClient, numSent);
        assertThat(numSent.get(), is(0));
    }

    @Test(expected=SAXParseException.class)
    public void testGarbage() throws Exception {
        InputStream stream = new ByteArrayInputStream("eehh".getBytes(StandardCharsets.UTF_8));
        AtomicInteger numSent = new AtomicInteger(0);
        FeedClient feedClient = mock(FeedClient.class);
        XmlFeedReader.read(stream, feedClient, numSent);
    }

    @Test
    public void testEncoding() throws Exception {
        InputStream stream = new ByteArrayInputStream("<?xml version=\"1.0\" encoding=\"utf8\"?><vespafeed><remove documentid=\"id:&amp;\"/></vespafeed>"
                        .getBytes(StandardCharsets.UTF_8));
        AtomicInteger numSent = new AtomicInteger(0);
        FeedClient feedClient = mock(FeedClient.class);

        doAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                String docId = (String) args[0];
                CharSequence value = (CharSequence)args[1];
                assertThat(value.toString(), is("<remove documentid=\"id:&amp;\"></remove>"));
                assertThat(docId, is("id:&"));
                return null;
            }
        }).when(feedClient).stream(anyString(), any());
        XmlFeedReader.read(stream, feedClient, numSent);
        assertThat(numSent.get(), is(1));
    }

    private final String characterDocs = "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<!-- GENERATED VESPA-XML BY YSSTOXML -->\n" +
            "<!-- USE ONLY FOR BATCH INDEXING -->\n" +
            "<vespafeed>\n" +
            "  <document documenttype=\"simple\" documentid=\"id:test::&amp;http://www.e.no/matprat\">\n" +
            "                        <language><![CDATA[ja]]></language>\n" +
            "                        <title><![CDATA[test document1]]></title>\n" +
            "                        <description><![CDATA[Bjørnen' blåbær på øy nærheten.]]></description>\n" +
            "                        <date>1091356845</date>\n" +
            "                        <surl><![CDATA[http://www.eventyr.no/matprat]]></surl>\n" +
            "                </document>\n" +
            "\n" +
            "</vespafeed>\n";

    @Test
    public void testCharacterEndcoding() throws Exception {
        InputStream stream = new ByteArrayInputStream(characterDocs.getBytes(StandardCharsets.UTF_8));
        AtomicInteger numSent = new AtomicInteger(0);
        FeedClient feedClient = mock(FeedClient.class);
        final AtomicBoolean success = new AtomicBoolean(false);
        doAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                String docId = (String) args[0];
                CharSequence value = (CharSequence)args[1];
                assertThat(value.toString(), is(
                        "<document documenttype=\"simple\" documentid=\"id:test::&amp;http://www.e.no/matprat\">\n" +
                                "                        <language><![CDATA[ja]]></language>\n" +
                                "                        <title><![CDATA[test document1]]></title>\n" +
                                "                        <description><![CDATA[Bjørnen' blåbær på øy nærheten.]]></description>\n" +
                                "                        <date>1091356845</date>\n" +
                                "                        <surl><![CDATA[http://www.eventyr.no/matprat]]></surl>\n" +
                                "                </document>"));
                success.set(true);
                return null;
            }
        }).when(feedClient).stream(anyString(), any());
        XmlFeedReader.read(stream, feedClient, numSent);
        assertThat(numSent.get(), is(1));
        assert(success.get());
    }

    @Test
    public void testRealData() throws Exception {
        InputStream inputStream = XmlFeedReaderTest.class.getResourceAsStream(feedResource);
        BufferedInputStream bis = new BufferedInputStream(inputStream);
        AtomicInteger numSent = new AtomicInteger(0);
        FeedClient feedClient = mock(FeedClient.class);

        XmlFeedReader.read(bis, feedClient, numSent);
        assertThat(numSent.get(), is(6));
    }

    private static class XmlTestFeedClient implements FeedClient {

        public List<String> documentIds = new ArrayList<>();
        public List<CharSequence> datas = new ArrayList<>();
        public List<Object> contexts = new ArrayList<>();

        @Override
        public void stream(String documentId, CharSequence documentData) {
            stream(documentId, documentData, null);
        }

        @Override
        public void stream(String documentId, String operationId, CharSequence documentData, Object context) {
            documentIds.add(documentId);
            datas.add(documentData);
            contexts.add(context);
        }


        @Override
        public void close() { }

        @Override
        public String getStatsAsJson() { return null; }
    }

    // Only for xml with single doc.
    private void verifyNoTransformationOfXml(String filename) throws Exception {
        InputStream inputStream = XmlFeedReaderTest.class.getResourceAsStream(filename);
        BufferedInputStream bis = new BufferedInputStream(inputStream);
        AtomicInteger numSent = new AtomicInteger(0);
        XmlTestFeedClient feedClient = new XmlTestFeedClient();
        XmlFeedReader.read(bis, feedClient, numSent);
        assertThat(numSent.get(), is(1));
        String document = feedClient.datas.get(0).toString();

        InputStream inputStream2 = XmlFeedReaderTest.class.getResourceAsStream(filename);
        String rawXML = new java.util.Scanner(inputStream2, "UTF-8").useDelimiter("\\A").next();

        String rawDoc = rawXML.toString().split("<document")[1].split("</document>")[0];
        assertThat(rawDoc.length() > 30, is(true));

        String decodedRawXml = StringEscapeUtils.unescapeXml(rawDoc);
        String decodedDoc = StringEscapeUtils.unescapeXml(document);

        assertThat(decodedDoc, containsString(decodedRawXml));
    }

    @Test public void testCData() throws Exception {
        verifyNoTransformationOfXml(feedResource2);
    }

    @Test public void testPCData() throws Exception {
        verifyNoTransformationOfXml(feedResource3);
    }

    @Test public void testAposData() throws Exception {
        verifyNoTransformationOfXml(feedResource4);
    }

}
