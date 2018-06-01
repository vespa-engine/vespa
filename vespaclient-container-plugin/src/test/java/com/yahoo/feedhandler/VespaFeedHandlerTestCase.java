// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.docproc.CallStack;
import com.yahoo.jdisc.HeaderFields;
import com.yahoo.messagebus.*;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.container.Container;
import com.yahoo.docproc.*;
import com.yahoo.docproc.jdisc.DocumentProcessingHandler;
import com.yahoo.docproc.jdisc.DocumentProcessingHandlerParameters;
import com.yahoo.document.*;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.documentapi.messagebus.loadtypes.LoadType;
import com.yahoo.documentapi.messagebus.protocol.*;
import com.yahoo.feedapi.DummySessionFactory;
import com.yahoo.feedapi.FeedContext;
import com.yahoo.feedapi.MessagePropertyProcessor;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.vespaclient.ClusterDef;
import com.yahoo.vespaclient.ClusterList;
import com.yahoo.vespaclient.config.FeederConfig;
import org.junit.After;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("deprecation") // Tests a deprecated class
public class VespaFeedHandlerTestCase {

    private VespaFeedHandler feedHandler;
    private VespaFeedHandlerRemove removeHandler;
    private VespaFeedHandlerStatus statusHandler;
    private VespaFeedHandlerRemoveLocation removeLocationHandler;
    private FeedContext context;

    private DummySessionFactory factory;
    private final String xmlFilesPath = "src/test/files/feedhandler/";

    public void setup(com.yahoo.messagebus.Error e, LoadTypeConfig loadTypeCfg,
                      boolean autoReply,
                      DummySessionFactory.ReplyFactory autoReplyFactory) throws Exception {
        DocumentTypeManager docMan = new DocumentTypeManager();
        DocumentTypeManagerConfigurer.configure(docMan, "file:" + xmlFilesPath + "documentmanager.cfg");

        if (autoReply) {
            if (autoReplyFactory != null) {
                factory = DummySessionFactory.createWithAutoReplyFactory(autoReplyFactory);
            } else {
                factory = DummySessionFactory.createWithErrorAutoReply(e);
            }
        } else {
            factory = DummySessionFactory.createDefault();
        }

        context = new FeedContext(new MessagePropertyProcessor(new FeederConfig(new FeederConfig.Builder()), loadTypeCfg), factory, docMan, new ClusterList(), new NullFeedMetric());

        Executor threadPool = Executors.newCachedThreadPool();
        feedHandler = new VespaFeedHandler(context, threadPool);
        removeHandler = new VespaFeedHandlerRemove(context, threadPool);
        statusHandler = new VespaFeedHandlerStatus(context, false, false, threadPool);
        removeLocationHandler = new VespaFeedHandlerRemoveLocation(context, threadPool);

        CallStack dpCallstack = new CallStack("bar");
        dpCallstack.addLast(new TestDocProc());
        dpCallstack.addLast(new TestLaterDocProc());

        DocprocService myservice = new DocprocService("bar");
        myservice.setCallStack(dpCallstack);
        myservice.setInService(true);

        ComponentRegistry<DocprocService> registry = new ComponentRegistry<DocprocService>();
        registry.register(new ComponentId(myservice.getName()), myservice);

        DocumentProcessingHandler handler = new DocumentProcessingHandler(registry,
                new ComponentRegistry<>(), new ComponentRegistry<>(),
                new DocumentProcessingHandlerParameters());

        Container container = Container.get();
        ComponentRegistry<RequestHandler> requestHandlerComponentRegistry = new ComponentRegistry<>();
        requestHandlerComponentRegistry.register(new ComponentId(DocumentProcessingHandler.class.getName()), handler);
        container.setRequestHandlerRegistry(requestHandlerComponentRegistry);
    }

    public void setup(com.yahoo.messagebus.Error e) throws Exception {
        setup(e, new LoadTypeConfig(new LoadTypeConfig.Builder()), true, null);
    }

    public void setupWithReplyFactory(DummySessionFactory.ReplyFactory autoReplyFactory) throws Exception {
        setup(null, new LoadTypeConfig(new LoadTypeConfig.Builder()), true, autoReplyFactory);
    }

    public void setup() throws Exception {
        setup(null, new LoadTypeConfig(new LoadTypeConfig.Builder()), false, null);
    }

    @After
    public void resetContainer() {
        Container.resetInstance();
    }


    @Test
    public void testLoadTypes() throws Exception {
        List<LoadTypeConfig.Type.Builder> typeBuilder = new ArrayList<>();
        typeBuilder.add(new LoadTypeConfig.Type.Builder().id(1234).name("foo").priority("VERY_LOW"));
        typeBuilder.add(new LoadTypeConfig.Type.Builder().id(4567).name("bar").priority("NORMAL_3"));

        setup(null, new LoadTypeConfig(new LoadTypeConfig.Builder().type(typeBuilder)), true, null);

        {
            Result res = testRequest(HttpRequest.createTestRequest("remove?id=doc:test:removeme&loadtype=foo", com.yahoo.jdisc.http.HttpRequest.Method.PUT));
            assertEquals(1, res.messages.size());

            Message m = res.messages.get(0);
            assertEquals(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, m.getType());
            DocumentId d = ((RemoveDocumentMessage)m).getDocumentId();
            assertEquals("doc:test:removeme", d.toString());
            assertEquals(new LoadType(1234, "foo", DocumentProtocol.Priority.VERY_LOW), ((DocumentMessage)m).getLoadType());
            assertEquals(DocumentProtocol.Priority.VERY_LOW, ((DocumentMessage)m).getPriority());
        }
    }

    @Test
    public void testPostXML() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test10b.xml", "feed?");

        assertEquals(2, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10a", d.toString());
        }
        {
                Message m = res.messages.get(1);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10b", d.toString());
        }

        assertTrue(res.output.contains("count=\"2\""));
        assertTrue(res.error == null);
    }

    @Test
    public void testPostXMLAsync() throws Exception {
        setup();
        Result res = testFeed(xmlFilesPath + "test10b.xml", "feed?asynchronous=true");

        assertEquals(2, res.messages.size());

        {
            Message m = res.messages.get(0);
            assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
            DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
            assertEquals("doc:news:http://news10a", d.toString());
        }
        {
            Message m = res.messages.get(1);
            assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
            DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
            assertEquals("doc:news:http://news10b", d.toString());
        }

        // Should not have metrics at this point.
        assertTrue(!res.output.contains("count=\"2\""));
        assertTrue(res.error == null);
    }


    @Test
    public void testPostGZIPedXML() throws Exception {
        setup(null);
        Result res = testFeedGZIP(xmlFilesPath + "test10b.xml", "feed?");

        assertEquals(2, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10a", d.toString());
        }
        {
                Message m = res.messages.get(1);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10b", d.toString());
        }

        assertTrue(res.error == null);
    }

    @Test
    public void testDocProc() throws Exception {
        setup(null);

        Result res = testFeed(xmlFilesPath + "test10b.xml", "feed?docprocchain=bar");

        assertEquals(2, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                Document d = ((PutDocumentMessage)m).getDocumentPut().getDocument();

                assertEquals("doc:news:http://news10a", d.getId().toString());
                assertEquals(new IntegerFieldValue(1234), d.getFieldValue("last_downloaded"));
        }
        {
                Message m = res.messages.get(1);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                Document d = ((PutDocumentMessage)m).getDocumentPut().getDocument();

                assertEquals("doc:news:http://news10b", d.getId().toString());
                assertEquals(new IntegerFieldValue(1234), d.getFieldValue("last_downloaded"));
        }
    }

    @Test
    public void testPostXMLVariousTypes() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test10.xml", "feed?");

        assertEquals(5, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10a", d.toString());
        }
        {
                Message m = res.messages.get(1);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10b", d.toString());
        }

        {
                Message m = res.messages.get(2);
                assertEquals(DocumentProtocol.MESSAGE_UPDATEDOCUMENT, m.getType());
                DocumentId d = ((UpdateDocumentMessage)m).getDocumentUpdate().getId();
                assertEquals("doc:news:http://news10c", d.toString());
        }
        {
                Message m = res.messages.get(3);
                assertEquals(DocumentProtocol.MESSAGE_UPDATEDOCUMENT, m.getType());
                DocumentId d = ((UpdateDocumentMessage)m).getDocumentUpdate().getId();
                assertEquals("doc:news:http://news10d", d.toString());
        }
        {
                Message m = res.messages.get(4);
                assertEquals(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, m.getType());
                DocumentId d = ((RemoveDocumentMessage)m).getDocumentId();
                assertEquals("doc:news:http://news10e", d.toString());
        }

        String val = res.output.replaceAll("<([a-z]+).*count=\"([0-9]+)\".*/", "<$1 count=\"$2\"/");

        assertEquals("<result>\n" +
                "\n" +
                "  <route name=\"default\">\n" +
                "    <total>\n" +
                "      <latency count=\"5\"/>\n" +
                "      <count count=\"5\"/>\n" +
                "    </total>\n" +
                "    <putdocument>\n" +
                "      <latency count=\"2\"/>\n" +
                "      <count count=\"2\"/>\n" +
                "    </putdocument>\n" +
                "    <updatedocument>\n" +
                "      <latency count=\"2\"/>\n" +
                "      <count count=\"2\"/>\n" +
                "    </updatedocument>\n" +
                "    <removedocument>\n" +
                "      <latency count=\"1\"/>\n" +
                "      <count count=\"1\"/>\n" +
                "    </removedocument>\n" +
                "  </route>\n" +
                "\n" +
                "</result>\n", val);
    }

    @Test
    public void testStatusPage() throws Exception {
        setup(null);

        testFeed(xmlFilesPath + "test10b.xml", "feed?docprocchain=bar");
        testFeed(xmlFilesPath + "test10.xml", "feed?");
        testFeed(xmlFilesPath + "test10.xml", "feed?route=storage");
        testFeed(xmlFilesPath + "test_removes", "remove?");

        assertEquals(2, factory.sessionsCreated());
        Result res = testRequest(HttpRequest.createTestRequest("feedstatus?", com.yahoo.jdisc.http.HttpRequest.Method.PUT));

        String val = res.output.replaceAll("<([a-z]+).*count=\"([0-9]+)\".*/", "<$1 count=\"$2\"/");
        val = val.replaceAll("to=\"[0-9]*\"", "to=\"0\"");

        assertEquals("<status>\n" +
                "\n" +
                "  <snapshot name=\"Total metrics from start until current time\" from=\"0\" to=\"0\" period=\"0\">\n" +
                "    <routes>\n" +
                "      <route name=\"total\">\n" +
                "        <total>\n" +
                "          <latency count=\"14\"/>\n" +
                "          <count count=\"14\"/>\n" +
                "        </total>\n" +
                "        <putdocument>\n" +
                "          <latency count=\"6\"/>\n" +
                "          <count count=\"6\"/>\n" +
                "        </putdocument>\n" +
                "        <updatedocument>\n" +
                "          <latency count=\"4\"/>\n" +
                "          <count count=\"4\"/>\n" +
                "        </updatedocument>\n" +
                "        <removedocument>\n" +
                "          <latency count=\"4\"/>\n" +
                "          <count count=\"4\"/>\n" +
                "        </removedocument>\n" +
                "      </route>\n" +
                "      <route name=\"default\">\n" +
                "        <total>\n" +
                "          <latency count=\"9\"/>\n" +
                "          <count count=\"9\"/>\n" +
                "        </total>\n" +
                "        <putdocument>\n" +
                "          <latency count=\"4\"/>\n" +
                "          <count count=\"4\"/>\n" +
                "        </putdocument>\n" +
                "        <updatedocument>\n" +
                "          <latency count=\"2\"/>\n" +
                "          <count count=\"2\"/>\n" +
                "        </updatedocument>\n" +
                "        <removedocument>\n" +
                "          <latency count=\"3\"/>\n" +
                "          <count count=\"3\"/>\n" +
                "        </removedocument>\n" +
                "      </route>\n" +
                "      <route name=\"storage\">\n" +
                "        <total>\n" +
                "          <latency count=\"5\"/>\n" +
                "          <count count=\"5\"/>\n" +
                "        </total>\n" +
                "        <putdocument>\n" +
                "          <latency count=\"2\"/>\n" +
                "          <count count=\"2\"/>\n" +
                "        </putdocument>\n" +
                "        <updatedocument>\n" +
                "          <latency count=\"2\"/>\n" +
                "          <count count=\"2\"/>\n" +
                "        </updatedocument>\n" +
                "        <removedocument>\n" +
                "          <latency count=\"1\"/>\n" +
                "          <count count=\"1\"/>\n" +
                "        </removedocument>\n" +
                "      </route>\n" +
                "    </routes>\n" +
                "  </snapshot>\n" +
                "\n" +
                "</status>\n", val);
    }

    @Test
    public void testStatusPage2() throws Exception {
        setup(null);

        testFeed(xmlFilesPath + "test10b.xml", "feed?docprocchain=bar");
        testFeed(xmlFilesPath + "test10.xml", "feed?");
        testFeed(xmlFilesPath + "test10.xml", "feed?route=storage");
        testFeed(xmlFilesPath + "test_removes", "remove?");

        assertEquals(2, factory.sessionsCreated());
        Result res = testRequest(HttpRequest.createTestRequest("feed?status", com.yahoo.jdisc.http.HttpRequest.Method.PUT));

        String val = res.output.replaceAll("<([a-z]+).*count=\"([0-9]+)\".*/", "<$1 count=\"$2\"/");
        val = val.replaceAll("to=\"[0-9]*\"", "to=\"0\"");

        assertEquals("<status>\n" +
                "\n" +
                "  <routes>\n" +
                "    <route name=\"total\" description=\"Messages sent to all routes\">\n" +
                "      <total description=\"All kinds of messages sent to the given route\">\n" +
                "        <latency count=\"14\"/>\n" +
                "        <count count=\"14\"/>\n" +
                "        <ignored count=\"0\"/>\n" +
                "      </total>\n" +
                "      <putdocument>\n" +
                "        <latency count=\"6\"/>\n" +
                "        <count count=\"6\"/>\n" +
                "        <ignored count=\"0\"/>\n" +
                "      </putdocument>\n" +
                "      <updatedocument>\n" +
                "        <latency count=\"4\"/>\n" +
                "        <count count=\"4\"/>\n" +
                "        <ignored count=\"0\"/>\n" +
                "      </updatedocument>\n" +
                "      <removedocument>\n" +
                "        <latency count=\"4\"/>\n" +
                "        <count count=\"4\"/>\n" +
                "        <ignored count=\"0\"/>\n" +
                "      </removedocument>\n" +
                "    </route>\n" +
                "    <route name=\"default\" description=\"Messages sent to the named route\">\n" +
                "      <total description=\"All kinds of messages sent to the given route\">\n" +
                "        <latency count=\"9\"/>\n" +
                "        <count count=\"9\"/>\n" +
                "        <ignored count=\"0\"/>\n" +
                "      </total>\n" +
                "      <putdocument>\n" +
                "        <latency count=\"4\"/>\n" +
                "        <count count=\"4\"/>\n" +
                "        <ignored count=\"0\"/>\n" +
                "      </putdocument>\n" +
                "      <updatedocument>\n" +
                "        <latency count=\"2\"/>\n" +
                "        <count count=\"2\"/>\n" +
                "        <ignored count=\"0\"/>\n" +
                "      </updatedocument>\n" +
                "      <removedocument>\n" +
                "        <latency count=\"3\"/>\n" +
                "        <count count=\"3\"/>\n" +
                "        <ignored count=\"0\"/>\n" +
                "      </removedocument>\n" +
                "    </route>\n" +
                "    <route name=\"storage\" description=\"Messages sent to the named route\">\n" +
                "      <total description=\"All kinds of messages sent to the given route\">\n" +
                "        <latency count=\"5\"/>\n" +
                "        <count count=\"5\"/>\n" +
                "        <ignored count=\"0\"/>\n" +
                "      </total>\n" +
                "      <putdocument>\n" +
                "        <latency count=\"2\"/>\n" +
                "        <count count=\"2\"/>\n" +
                "        <ignored count=\"0\"/>\n" +
                "      </putdocument>\n" +
                "      <updatedocument>\n" +
                "        <latency count=\"2\"/>\n" +
                "        <count count=\"2\"/>\n" +
                "        <ignored count=\"0\"/>\n" +
                "      </updatedocument>\n" +
                "      <removedocument>\n" +
                "        <latency count=\"1\"/>\n" +
                "        <count count=\"1\"/>\n" +
                "        <ignored count=\"0\"/>\n" +
                "      </removedocument>\n" +
                "    </route>\n" +
                "  </routes>\n" +
                "\n" +
                "</status>\n", val);
    }

    @Test
    public void testMetricForIgnoredDocumentsIsIncreased() throws Exception {
        DummySessionFactory.ReplyFactory replyFactory = new DummySessionFactory.ReplyFactory() {
            @Override
            public Reply createReply(Message m) {
                return new DocumentIgnoredReply();
            }
        };
        setupWithReplyFactory(replyFactory);
        Result res = testFeed(xmlFilesPath + "test10b.xml", "feed?");
        assertEquals(2, res.messages.size());

        String val = res.output.replaceAll("<([a-z]+).*count=\"([0-9]+)\".*/", "<$1 count=\"$2\"/");

        assertEquals("<result>\n" +
                "\n" +
                "  <route name=\"default\">\n" +
                "    <total>\n" +
                "      <ignored count=\"2\"/>\n" +
                "    </total>\n" +
                "    <putdocument>\n" +
                "      <ignored count=\"2\"/>\n" +
                "    </putdocument>\n" +
                "  </route>\n" +
                "\n" +
                "</result>\n", val);
    }

    @Test
    public void testPostXMLWithMBusFailureAllowed() throws Exception {
        setup(new com.yahoo.messagebus.Error(DocumentProtocol.ERROR_BUCKET_DELETED, "Hello world in <document>"));
        Result res = testFeed(xmlFilesPath + "test10b.xml", "feed?abortonfeederror=false");

        assertEquals(2, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10a", d.toString());
        }
        {
                Message m = res.messages.get(1);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10b", d.toString());
        }

        String val = res.output.replaceAll("average=\"[0-9]*\" last=\"[0-9]*\" min=\"[0-9]*\" max=\"[0-9]*\" ", "");
        System.out.println(val);

        assertEquals("<result>\n" +
                "\n" +
                "  <route name=\"default\">\n" +
                "    <total>\n" +
                "      <errors>\n" +
                "        <error name=\"total\" count=\"2\"/>\n" +
                "        <error name=\"BUCKET_DELETED\" count=\"2\"/>\n" +
                "      </errors>\n" +
                "    </total>\n" +
                "    <putdocument>\n" +
                "      <errors>\n" +
                "        <error name=\"total\" count=\"2\"/>\n" +
                "        <error name=\"BUCKET_DELETED\" count=\"2\"/>\n" +
                "      </errors>\n" +
                "    </putdocument>\n" +
                "  </route>\n\n" +
                "  <errors count=\"2\">\n" +
                "    <error message=\"PUT[doc:news:http://news10a] [BUCKET_DELETED] Hello world in &lt;document&gt;\"/>\n" +
                "    <error message=\"PUT[doc:news:http://news10b] [BUCKET_DELETED] Hello world in &lt;document&gt;\"/>\n" +
                "  </errors>\n" +
                "\n" +
                "</result>\n", val);

        assertTrue(res.error != null);
        assertTrue(res.errorCount > 0);
    }

    @Test
    public void testPostXMLWithMBusFailure() throws Exception {
        setup(new com.yahoo.messagebus.Error(32, "Hello world"));
        Result res = testFeed(xmlFilesPath + "test10b.xml", "feed?");

        assertEquals(1, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10a", d.toString());
        }

        String val = res.output.replaceAll("average=\"[0-9]*\" last=\"[0-9]*\" min=\"[0-9]*\" max=\"[0-9]*\" ", "");
        assertEquals("<result>\n" +
                "\n" +
                "  <route name=\"default\">\n" +
                "    <total>\n" +
                "      <errors>\n" +
                "        <error name=\"total\" count=\"1\"/>\n" +
                "        <error name=\"UNKNOWN(32)\" count=\"1\"/>\n" +
                "      </errors>\n" +
                "    </total>\n" +
                "    <putdocument>\n" +
                "      <errors>\n" +
                "        <error name=\"total\" count=\"1\"/>\n" +
                "        <error name=\"UNKNOWN(32)\" count=\"1\"/>\n" +
                "      </errors>\n" +
                "    </putdocument>\n" +
                "  </route>\n\n" +
                "  <errors count=\"1\">\n" +
                "    <error message=\"PUT[doc:news:http://news10a] [UNKNOWN(32)] Hello world\"/>\n" +
                "  </errors>\n" +
                "\n" +
                "</result>\n", val);

        assertTrue(res.error != null);
        assertTrue(res.errorCount > 0);
    }

    @Test
    public void testPostXMLWithIllegalDocId() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test_bogus_docid.xml", "feed?");

        assertEquals(1, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10a", d.toString());
        }
    }

    @Test
    public void testPostXMLWithIllegalDocIdAllowFailure() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test_bogus_docid.xml", "feed?abortondocumenterror=false");

        assertEquals(2, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10a", d.toString());
        }

        {
                Message m = res.messages.get(1);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:ok", d.toString());
        }
    }

    @Test
    public void testPostUnparseableXML() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test_bogus_xml.xml", "feed?");

        assertEquals(1, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10a", d.toString());
        }
    }

    @Test
    public void testOverrides() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test10b.xml", "feed?timeout=2.222&route=storage&priority=HIGH_2&totaltimeout=-1");

        assertEquals(2, res.messages.size());

        for (Message m : res.messages) {
            assertEquals(2222, m.getTimeRemaining());
            assertEquals(Route.parse("storage"), m.getRoute());
            assertEquals(DocumentProtocol.Priority.HIGH_2, ((DocumentMessage)m).getPriority());
        }
    }

    @Test
    public void testTimeoutWithNoUpperBound() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test10b.xml", "feed?timeout=2.222&totaltimeout=-1");

        assertEquals(2, res.messages.size());

        for (Message m : res.messages) {
            assertEquals(2222, m.getTimeRemaining());
        }
    }

    @Test
    public void testTimeout() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test10b.xml", "feed?timeout=2.222");

        assertEquals(2, res.messages.size());

        for (Message m : res.messages) {
            assertTrue(2222 >= m.getTimeRemaining());
        }
    }

    @Test
    public void testTotalTimeout() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test10b.xml", "feed?totaltimeout=2.222");

        assertEquals(2, res.messages.size());

        for (Message m : res.messages) {
            assertTrue(2222 >= m.getTimeRemaining());
        }
    }

    @Test
    public void testTotalTimeoutAndNormalTimeout() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test10b.xml", "feed?totaltimeout=1000&timeout=2.222");

        assertEquals(2, res.messages.size());

        for (Message m : res.messages) {
            assertEquals(2222, m.getTimeRemaining());
        }
    }

    @Test
    public void testBogusPriority() throws Exception {
        try {
            setup(null);
            Result res = testFeed(xmlFilesPath + "test10b.xml", "feed?timeout=2222&route=storage&priority=HIPSTER_DOOFUS");
            assertTrue(false);
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testPostXMLWithIllegalDocIdFirst() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test_bogus_docid_first.xml", "feed?");

        assertEquals(0, res.messages.size());
    }

    @Test
    public void testPostXMLWithIllegalDocIdFirstNoAbort() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test_bogus_docid_first.xml", "feed?abortondocumenterror=false");

        assertEquals(1, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_PUTDOCUMENT, m.getType());
                DocumentId d = ((PutDocumentMessage)m).getDocumentPut().getDocument().getId();
                assertEquals("doc:news:http://news10a", d.toString());
        }
    }

    @Test
    public void testSimpleRemove() throws Exception {
        setup(null);
        Result res = testRequest(HttpRequest.createTestRequest("remove?id=doc:test:removeme", com.yahoo.jdisc.http.HttpRequest.Method.PUT));
        assertEquals(1, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, m.getType());
                DocumentId d = ((RemoveDocumentMessage)m).getDocumentId();
                assertEquals("doc:test:removeme", d.toString());
        }
    }

    @Test
    public void testRemoveUser() throws Exception {
        setup(null);

        context.getClusterList().getStorageClusters().add(new ClusterDef("storage", "storage/cluster.storage"));
        Result res = testRequest(HttpRequest.createTestRequest("removelocation?user=1234", com.yahoo.jdisc.http.HttpRequest.Method.PUT));
        assertEquals(1, res.messages.size());

        {
            Message m = res.messages.get(0);
            assertEquals(DocumentProtocol.MESSAGE_REMOVELOCATION, m.getType());
            String selection = ((RemoveLocationMessage)m).getDocumentSelection();
            assertEquals("storage", m.getRoute().toString());
            assertEquals("id.user=1234", selection);
        }
    }

    @Test
    public void testRemoveGroup() throws Exception {
        setup(null);
        context.getClusterList().getStorageClusters().add(new ClusterDef("storage", "storage/cluster.storage"));
        Result res = testRequest(HttpRequest.createTestRequest("removelocation?group=foo", com.yahoo.jdisc.http.HttpRequest.Method.PUT));
        assertEquals(1, res.messages.size());

        {
            Message m = res.messages.get(0);
            assertEquals(DocumentProtocol.MESSAGE_REMOVELOCATION, m.getType());
            String selection = ((RemoveLocationMessage)m).getDocumentSelection();
            assertEquals("storage", m.getRoute().toString());
            assertEquals("id.group=\"foo\"", selection);
        }
    }

    @Test
    public void testRemoveBadSyntax() throws Exception {
        setup(null);
        context.getClusterList().getStorageClusters().add(new ClusterDef("storage", "storage/cluster.storage"));
        Result res = testRequest(HttpRequest.createTestRequest("removelocation?group=foo&user=12345", com.yahoo.jdisc.http.HttpRequest.Method.PUT));
        assertEquals(0, res.messages.size());
        assertTrue(res.error.toString().contains("Exactly one of"));
    }

    @Test
    public void testRemoveGroupMultipleClusters() throws Exception {
        setup(null);
        context.getClusterList().getStorageClusters().add(new ClusterDef("storage1", "storage/cluster.storage1"));
        context.getClusterList().getStorageClusters().add(new ClusterDef("storage2", "storage/cluster.storage2"));
        Result res = testRequest(HttpRequest.createTestRequest("removelocation?group=foo", com.yahoo.jdisc.http.HttpRequest.Method.PUT));
        assertEquals(0, res.messages.size());
        assertTrue(res.error.toString().contains("More than one"));
    }

    @Test
    public void testRemoveGroupNoClusters() throws Exception {
        setup(null);
        Result res = testRequest(HttpRequest.createTestRequest("removelocation?group=foo", com.yahoo.jdisc.http.HttpRequest.Method.PUT));
        assertEquals(0, res.messages.size());
        assertTrue(res.error.toString().contains("No storage clusters"));
    }

    @Test
    public void testRemoveSelection() throws Exception {
        setup(null);
        context.getClusterList().getStorageClusters().add(new ClusterDef("storage", "storage/cluster.storage"));
        Result res = testRequest(HttpRequest.createTestRequest("removelocation?selection=id.user=1234", com.yahoo.jdisc.http.HttpRequest.Method.PUT));
        assertEquals(1, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_REMOVELOCATION, m.getType());
                String selection = ((RemoveLocationMessage)m).getDocumentSelection();
                assertEquals("id.user=1234", selection);
        }
    }

    @Test
    public void testSimpleRemoveIndex() throws Exception {
        setup(null);
        Result res = testRequest(HttpRequest.createTestRequest("remove?id[0]=doc:test:removeme", com.yahoo.jdisc.http.HttpRequest.Method.PUT));
        assertEquals(1, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, m.getType());
                DocumentId d = ((RemoveDocumentMessage)m).getDocumentId();
                assertEquals("doc:test:removeme", d.toString());
        }
    }

   @Test
   public void testPostRemove() throws Exception {
        setup(null);
        Result res = testFeed(xmlFilesPath + "test_removes", "remove?");
        assertEquals(2, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, m.getType());
                DocumentId d = ((RemoveDocumentMessage)m).getDocumentId();
                assertEquals("doc:test:remove1", d.toString());
        }

        {
                Message m = res.messages.get(1);
                assertEquals(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, m.getType());
                DocumentId d = ((RemoveDocumentMessage)m).getDocumentId();
                assertEquals("doc:test:remove2", d.toString());
        }
    }

    @Test
    public void testRemoveBogusId() throws Exception {
        try {
            setup(null);
            Result res = testRequest(HttpRequest.createTestRequest("remove?id=unknowndoc:test:removeme", com.yahoo.jdisc.http.HttpRequest.Method.PUT));
            assertTrue(false);
        } catch (Exception e) {
        }
    }

    @Test
    public void testMultiRemove() throws Exception {
        setup(null);
        Result res = testRequest(HttpRequest.createTestRequest("remove?id[0]=doc:test:removeme&id[1]=doc:test:remove2&id[2]=doc:test:remove3", com.yahoo.jdisc.http.HttpRequest.Method.PUT));
        assertEquals(3, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, m.getType());
                DocumentId d = ((RemoveDocumentMessage)m).getDocumentId();
                assertEquals("doc:test:removeme", d.toString());
        }

        {
                Message m = res.messages.get(1);
                assertEquals(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, m.getType());
                DocumentId d = ((RemoveDocumentMessage)m).getDocumentId();
                assertEquals("doc:test:remove2", d.toString());
        }

        {
                Message m = res.messages.get(2);
                assertEquals(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, m.getType());
                DocumentId d = ((RemoveDocumentMessage)m).getDocumentId();
                assertEquals("doc:test:remove3", d.toString());
        }
    }

    @Test
    public void testMultiRemoveSameDoc() throws Exception {
        setup(null);
        Result res = testRequest(HttpRequest.createTestRequest("remove?id[0]=userdoc:footype:1234:foo&id[1]=userdoc:footype:1234:foo", com.yahoo.jdisc.http.HttpRequest.Method.PUT));
        assertEquals(2, res.messages.size());

        {
                Message m = res.messages.get(0);
                assertEquals(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, m.getType());
        }

        {
                Message m = res.messages.get(1);
                assertEquals(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, m.getType());
        }
    }

    @Test
    public void testFeedHandlerStatusCreation() throws Exception {
        VespaFeedHandlerStatus status = new VespaFeedHandlerStatus(
                new FeedContext(new MessagePropertyProcessor(
                        new FeederConfig(new FeederConfig.Builder()),
                        new LoadTypeConfig(new LoadTypeConfig.Builder())),
                        factory, null, new ClusterList(), new NullFeedMetric()),
                true, true,
                Executors.newCachedThreadPool());
    }

    private class TestDocProc extends DocumentProcessor {
        @Override
        public Progress process(Processing processing) {
            for (DocumentOperation op : processing.getDocumentOperations())  {
                if (op instanceof DocumentPut) {
                    Document document = ((DocumentPut)op).getDocument();
                    document.setFieldValue("last_downloaded", new IntegerFieldValue(1234));
                }
            }
            return Progress.DONE;
        }
    }

    private class TestLaterDocProc extends DocumentProcessor {
        private final Logger log = Logger.getLogger(TestLaterDocProc.class.getName());

        private int counter = 0;
        @Override
        public Progress process(Processing processing) {
            synchronized (this) {
                counter++;
                if (counter % 2 == 1) {
                    log.info("Returning LATER.");
                    return Progress.LATER;
                }
                log.info("Returning DONE.");
                return Progress.DONE;
            }
        }
    }

    private Result testRequest(HttpRequest req) throws Exception {
        HttpResponse response = null;
        String feedPrefix = "feed";
        String removePrefix = "remove";
        String feedStatusPrefix = "feedstatus";
        String removeLocationPrefix = "removelocation";

        if (req.getUri().getPath().startsWith(feedPrefix)) {
            response = feedHandler.handle(req);
        }
        if (req.getUri().getPath().startsWith(removePrefix)) {
            response = removeHandler.handle(req);
        }
        if (req.getUri().getPath().startsWith(feedStatusPrefix)) {
            response = statusHandler.handle(req);
        }
        if (req.getUri().getPath().startsWith(removeLocationPrefix)) {
            response = removeLocationHandler.handle(req);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.render(output);

        Result res = new Result();
        res.messages = factory.messages;
        res.output = new String(output.toByteArray());

        if (response instanceof FeedResponse) {
            FeedResponse feedResponse = (FeedResponse)response;
            res.error = feedResponse.getErrorMessageList().isEmpty() ? null : feedResponse.getErrorMessageList().get(0);
            res.errorCount = feedResponse.getErrorMessageList().size();
            assertTrue(feedResponse.isSuccess() == (res.errorCount == 0));
        }
        return res;
    }

    private Result testFeed(String xmlFile, String request) throws Exception {
        return testRequest(new FileRequest(new File(xmlFile), request).toRequest());
    }

    private Result testFeedGZIP(String xmlFile, String request) throws Exception {
        return testRequest(new FileRequest(new File(xmlFile), request, true).toRequest());
    }

    private class FileRequest {

        private final String req;
        private final File f;
        private boolean gzip = false;

        FileRequest(File f, String req) {
            this.req = req;
            this.f = f;
        }

        FileRequest(File f, String req, boolean gzip) {
            this.f = f;
            this.req = req;
            this.gzip = gzip;
        }

        public InputStream getData() {
            try {
                InputStream fileStream = new FileInputStream(f);
                if (gzip) {
                    // Not exactly pretty, but in lack of an elegant way of transcoding
                    ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
                    GZIPOutputStream compressed = new GZIPOutputStream(rawOut);
                    byte[] buffer = new byte[1024];
                    int read = -1;
                    while (true) {
                        read = fileStream.read(buffer);
                        if (read == -1) break;
                        compressed.write(buffer, 0, read);
                    }
                    compressed.finish();
                    compressed.flush();
                    rawOut.flush();
                    return new ByteArrayInputStream(rawOut.toByteArray());
                }
                return fileStream;
            } catch (Exception e) {
                return null;
            }
        }

        public void addHeaders(HeaderFields headers) {
            headers.add("Content-Type", "image/jpeg");
            if (gzip)
                headers.add("Content-Encoding", "gzip");
        }

        public HttpRequest toRequest() {
            HttpRequest request = HttpRequest.createTestRequest(req, com.yahoo.jdisc.http.HttpRequest.Method.GET, getData());
            addHeaders(request.getJDiscRequest().headers());
            return request;
        }

    }

    private class Result {
        private List<Message> messages;
        private String output;
        private com.yahoo.processing.request.ErrorMessage error;
        private int errorCount;
    }

}
