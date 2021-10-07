// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespafeeder;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;

import com.yahoo.clientmetrics.RouteMetricSet;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.feedapi.DummySessionFactory;
import com.yahoo.feedhandler.VespaFeedHandler;
import com.yahoo.text.Utf8;
import com.yahoo.vespaclient.config.FeederConfig;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VespaFeederTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testParseArgs() throws Exception {
        String argsS="--abortondataerror false --abortonsenderror false --file foo.xml --maxpending 10" +
                " --maxfeedrate 29 --mode benchmark --noretry --route e6 --timeout 13 --trace 4" +
                " --validate -v bar.xml --priority LOW_1";

        Arguments arguments = new Arguments(argsS.split(" "), DummySessionFactory.createWithAutoReply());

        FeederConfig config = arguments.getFeederConfig();
        assertEquals(false, config.abortondocumenterror());
        assertEquals(13.0, config.timeout(), 0.00001);
        assertEquals(false, config.retryenabled());
        assertEquals("e6", config.route());
        assertEquals(4, config.tracelevel());
        assertEquals(false, config.abortonsenderror());
        assertEquals(10, config.maxpendingdocs());
        assertEquals(29.0, config.maxfeedrate(), 0.0001);
        assertTrue(arguments.isVerbose());
        assertFalse(config.createifnonexistent());

        assertEquals("LOW_1", arguments.getPriority());
        assertEquals("benchmark", arguments.getMode());
        assertEquals("foo.xml", arguments.getFiles().get(0));
        assertEquals("bar.xml", arguments.getFiles().get(1));
    }

    @Test
    public void requireThatCreateIfNonExistentArgumentCanBeParsed() throws Exception {
        String argsS="--create-if-non-existent --file foo.xml";
        Arguments arguments = new Arguments(argsS.split(" "), DummySessionFactory.createWithAutoReply());
        assertTrue(arguments.getFeederConfig().createifnonexistent());
    }

    @Test
    public void requireThatnumThreadsBeParsed() throws Exception {
        String argsS="--numthreads 5";
        Arguments arguments = new Arguments(argsS.split(" "), DummySessionFactory.createWithAutoReply());
        assertEquals(5, arguments.getNumThreads());
        assertEquals(1, new Arguments("".split(" "), DummySessionFactory.createWithAutoReply()).getNumThreads());
    }

    @Test
    public void testHelp() throws Exception {
        String argsS="-h";

        try {
            new Arguments(argsS.split(" "), null);
            assertTrue(false);
        } catch (Arguments.HelpShownException e) {

        }
    }

    @Test
    public void requireCorrectInputTypeDetection() throws IOException {
        {
            BufferedInputStream b = new BufferedInputStream(
                    new ByteArrayInputStream(Utf8.toBytes("[]")));
            InputStreamRequest r = new InputStreamRequest(b);
            VespaFeeder.setJsonInput(r, b);
            assertEquals("true", r.getProperty(VespaFeedHandler.JSON_INPUT));
        }
        {
            BufferedInputStream b = new BufferedInputStream(
                    new ByteArrayInputStream(Utf8.toBytes("<document />")));
            InputStreamRequest r = new InputStreamRequest(b);
            VespaFeeder.setJsonInput(r, b);
            assertEquals("false", r.getProperty(VespaFeedHandler.JSON_INPUT));
        }
    }

    public void assertRenderErrorOutput(String expected, String[] errors) {
        ArrayList<String> l = new ArrayList<String>();
        l.addAll(Arrays.asList(errors));
        assertEquals(expected, VespaFeeder.renderErrors(l).getMessage());
    }

    @Test
    public void testRenderErrors() {
        {
            String[] errors = { "foo" };
            assertRenderErrorOutput("Errors:\n" +
                                    "-------\n" +
                                    "    foo\n", errors);
        }

        {
            String[] errors = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"};
            assertRenderErrorOutput("First 10 errors (of 11):\n" +
                    "------------------------\n" +
                    "    1\n    2\n    3\n    4\n    5\n    6\n    7\n    8\n    9\n    10\n", errors);
        }
    }

    public RouteMetricSet.ProgressCallback getProgressPrinter(String args) throws Exception {
        Arguments arguments = new Arguments(args.split(" "), DummySessionFactory.createWithAutoReply());
        return new VespaFeeder(arguments, null).createProgressCallback(System.out);
    }

    @Test
    public void testCreateProgressPrinter() throws Exception {
        assert(getProgressPrinter("--mode benchmark") instanceof BenchmarkProgressPrinter);
        assert(getProgressPrinter("") instanceof ProgressPrinter);
    }

    private static class FeedFixture {
        DummySessionFactory sessionFactory = DummySessionFactory.createWithAutoReply();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);
        DocumentTypeManager typeManager = new DocumentTypeManager();
        FeedFixture() {
            DocumentTypeManagerConfigurer.configure(typeManager, "file:src/test/files/documentmanager.cfg");
        }
    }

    @Test
    public void feedFile() throws Exception {
        FeedFixture f = new FeedFixture();
        Arguments arguments = new Arguments("--file src/test/files/myfeed.xml --priority LOW_1".split(" "), f.sessionFactory);
        new VespaFeeder(arguments, f.typeManager).parseFiles(System.in, f.printStream);

        assertEquals(3, f.sessionFactory.messages.size());
        assertEquals(DocumentProtocol.Priority.LOW_1, ((PutDocumentMessage)f.sessionFactory.messages.get(0)).getPriority());
        assertEquals("id:test:news::foo", ((PutDocumentMessage) f.sessionFactory.messages.get(0)).getDocumentPut().getDocument().getId().toString());
        DocumentUpdate update = ((UpdateDocumentMessage) f.sessionFactory.messages.get(1)).getDocumentUpdate();
        assertEquals("id:test:news::foo", update.getId().toString());
        assertFalse(update.getCreateIfNonExistent());
        assertEquals("id:test:news::foo", ((RemoveDocumentMessage) f.sessionFactory.messages.get(2)).getDocumentId().toString());

        assertTrue(f.outputStream.toString().contains("Messages sent to vespa"));
    }

    @Test
    public void feedJson() throws Exception {
        FeedFixture feedFixture = feed("src/test/files/myfeed.json", true);

        assertJsonFeedState(feedFixture);
    }

    protected void assertJsonFeedState(FeedFixture feedFixture) {
        assertEquals(3, feedFixture.sessionFactory.messages.size());
        assertEquals(DocumentProtocol.Priority.LOW_1, ((PutDocumentMessage)feedFixture.sessionFactory.messages.get(0)).getPriority());
        assertEquals("id:test:news::foo", ((PutDocumentMessage) feedFixture.sessionFactory.messages.get(0)).getDocumentPut().getDocument().getId().toString());
        DocumentUpdate update = ((UpdateDocumentMessage) feedFixture.sessionFactory.messages.get(1)).getDocumentUpdate();
        assertEquals("id:test:news::foo", update.getId().toString());
        assertFalse(update.getCreateIfNonExistent());
        assertEquals("id:test:news::foo", ((RemoveDocumentMessage) feedFixture.sessionFactory.messages.get(2)).getDocumentId().toString());

        assertTrue(feedFixture.outputStream.toString().contains("Messages sent to vespa"));
    }

    @Test
    public void requireThatCreateIfNonExistentArgumentIsUsed() throws Exception {
        FeedFixture f = new FeedFixture();
        Arguments arguments = new Arguments("--file src/test/files/myfeed.xml --create-if-non-existent".split(" "), f.sessionFactory);
        new VespaFeeder(arguments, f.typeManager).parseFiles(System.in, f.printStream);

        assertEquals(3, f.sessionFactory.messages.size());
        DocumentUpdate update = ((UpdateDocumentMessage) f.sessionFactory.messages.get(1)).getDocumentUpdate();
        assertTrue(update.getCreateIfNonExistent());
    }

    @Test
    public void feedMalformedJson() throws Exception {
        exception.expect(VespaFeeder.FeedErrorException.class);
        exception.expectMessage("JsonParseException");
        feed("src/test/files/malformedfeed.json", false);
    }

    protected FeedFixture feed(String feed, boolean abortOnDataError) throws Exception {
        String abortOnDataErrorArgument = abortOnDataError ? "" : " --abortondataerror no";
        FeedFixture feedFixture = new FeedFixture();
        Arguments arguments = new Arguments(("--file "
                + feed
                + " --priority LOW_1" + abortOnDataErrorArgument).split(" "), feedFixture.sessionFactory);
        new VespaFeeder(arguments, feedFixture.typeManager).parseFiles(System.in, feedFixture.printStream);
        return feedFixture;
    }
}
