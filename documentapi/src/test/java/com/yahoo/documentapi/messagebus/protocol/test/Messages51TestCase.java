// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.yahoo.component.Version;
import com.yahoo.document.BucketId;
import com.yahoo.document.DocumentId;
import com.yahoo.document.select.OrderingSpecification;
import com.yahoo.documentapi.messagebus.protocol.CreateVisitorMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentIgnoredReply;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentMessage;
import com.yahoo.text.Utf8;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class Messages51TestCase extends Messages50TestCase {

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Setup
    //
    ///////////////////////////////////////////////////////////////////////////////

    @Override
    protected void registerTests(Map<Integer, RunnableTest> out) {
        super.registerTests(out);

        // This list MUST mirror the list of routable factories from the DocumentProtocol constructor that support
        // version 5.0. When adding tests to this list, please KEEP THEM ORDERED alphabetically like they are now.
        out.put(DocumentProtocol.MESSAGE_CREATEVISITOR, new testCreateVisitorMessage());
        out.put(DocumentProtocol.MESSAGE_GETDOCUMENT, new testGetDocumentMessage());
        out.put(DocumentProtocol.REPLY_DOCUMENTIGNORED, new testDocumentIgnoredReply());
    }

    @Override
    protected Version version() {
        return new Version(5, 1);
    }

    @Override
    protected boolean shouldTestCoverage() {
        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Tests
    //
    ////////////////////////////////////////////////////////////////////////////////

    private static int BASE_MESSAGE_LENGTH = 5;

    public class testCreateVisitorMessage implements RunnableTest {

        @Override
        public void run() {
            CreateVisitorMessage msg = new CreateVisitorMessage("SomeLibrary", "myvisitor", "newyork", "london");
            msg.setDocumentSelection("true and false or true");
            msg.getParameters().put("myvar", Utf8.toBytes("somevalue"));
            msg.getParameters().put("anothervar", Utf8.toBytes("34"));
            msg.getBuckets().add(new BucketId(16, 1234));
            msg.setVisitRemoves(true);
            msg.setFieldSet("foo bar");
            msg.setVisitorOrdering(OrderingSpecification.DESCENDING);
            msg.setMaxBucketsPerVisitor(2);
            assertEquals(BASE_MESSAGE_LENGTH + 178, serialize("CreateVisitorMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (CreateVisitorMessage)deserialize("CreateVisitorMessage", DocumentProtocol.MESSAGE_CREATEVISITOR, lang);
                assertEquals("SomeLibrary", msg.getLibraryName());
                assertEquals("myvisitor", msg.getInstanceId());
                assertEquals("newyork", msg.getControlDestination());
                assertEquals("london", msg.getDataDestination());
                assertEquals("true and false or true", msg.getDocumentSelection());
                assertEquals(8, msg.getMaxPendingReplyCount());
                assertEquals(true, msg.getVisitRemoves());
                assertEquals("foo bar", msg.getFieldSet());
                assertEquals(false, msg.getVisitInconsistentBuckets());
                assertEquals(1, msg.getBuckets().size());
                assertEquals(new BucketId(16, 1234), msg.getBuckets().iterator().next());
                assertEquals("somevalue", Utf8.toString(msg.getParameters().get("myvar")));
                assertEquals("34", Utf8.toString(msg.getParameters().get("anothervar")));
                assertEquals(OrderingSpecification.DESCENDING, msg.getVisitorOrdering());
                assertEquals(2, msg.getMaxBucketsPerVisitor());
            }
        }
    }

    public class testGetDocumentMessage implements RunnableTest {

        @Override
        public void run() {
            GetDocumentMessage msg = new GetDocumentMessage(new DocumentId("doc:scheme:"), "foo bar");
            assertEquals(BASE_MESSAGE_LENGTH + 27, serialize("GetDocumentMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (GetDocumentMessage)deserialize("GetDocumentMessage", DocumentProtocol.MESSAGE_GETDOCUMENT, lang);
                assertEquals("doc:scheme:", msg.getDocumentId().toString());
                assertEquals("foo bar", msg.getFieldSet());
            }
        }
    }

    public class testDocumentIgnoredReply implements RunnableTest {

        @Override
        public void run() {
            DocumentIgnoredReply reply = new DocumentIgnoredReply();
            assertEquals(BASE_MESSAGE_LENGTH, serialize("DocumentIgnoredReply", reply));

            for (Language lang : LANGUAGES) {
                reply = (DocumentIgnoredReply)deserialize("DocumentIgnoredReply", DocumentProtocol.REPLY_DOCUMENTIGNORED, lang);
            }
        }
    }
}
