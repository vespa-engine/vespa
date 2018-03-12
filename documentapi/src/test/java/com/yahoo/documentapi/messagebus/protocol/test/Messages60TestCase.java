// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.yahoo.component.Version;
import com.yahoo.document.BucketId;
import com.yahoo.document.select.OrderingSpecification;
import com.yahoo.documentapi.messagebus.protocol.CreateVisitorMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.GetBucketListMessage;
import com.yahoo.documentapi.messagebus.protocol.StatBucketMessage;
import com.yahoo.text.Utf8;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class Messages60TestCase extends Messages52TestCase {

    @Override
    protected Version version() {
        return new Version(6, 221);
    }

    @Override
    protected boolean shouldTestCoverage() {
        return true;
    }

    @Override
    protected void registerTests(Map<Integer, MessagesTestBase.RunnableTest> out) {
        super.registerTests(out);

        // This list MUST mirror the list of routable factories from the DocumentProtocol constructor that support
        // version 6.0. When adding tests to this list, please KEEP THEM ORDERED alphabetically like they are now.

        out.put(DocumentProtocol.MESSAGE_CREATEVISITOR, new Messages60TestCase.testCreateVisitorMessage());
        out.put(DocumentProtocol.MESSAGE_STATBUCKET, new Messages60TestCase.testStatBucketMessage());
        out.put(DocumentProtocol.MESSAGE_GETBUCKETLIST, new Messages60TestCase.testGetBucketListMessage());
    }

    public class testCreateVisitorMessage implements RunnableTest {

        private static final String BUCKET_SPACE = "bjarne";

        // FIXME there is a large amount of code duplication across version tests, presumably
        // to ensure that fields survive across version boundaries, but it makes code very bloated.
        // TODO head towards a blissful Protobuf future instead...
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
            msg.setBucketSpace(BUCKET_SPACE);
            assertEquals(BASE_MESSAGE_LENGTH + 178 + serializedLength(BUCKET_SPACE), serialize("CreateVisitorMessage", msg));

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
                assertEquals(BUCKET_SPACE, msg.getBucketSpace());
            }
        }
    }

    public class testStatBucketMessage implements RunnableTest {

        private static final String BUCKET_SPACE = "andrei";

        @Override
        public void run() {
            StatBucketMessage msg = new StatBucketMessage(new BucketId(16, 123), "id.user=123");
            msg.setLoadType(null);
            msg.setBucketSpace(BUCKET_SPACE);
            assertEquals(BASE_MESSAGE_LENGTH + 27 + serializedLength(BUCKET_SPACE), serialize("StatBucketMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (StatBucketMessage)deserialize("StatBucketMessage", DocumentProtocol.MESSAGE_STATBUCKET, lang);
                assertEquals(new BucketId(16, 123), msg.getBucketId());
                assertEquals("id.user=123", msg.getDocumentSelection());
                assertEquals("default", msg.getLoadType().getName());
                assertEquals(BUCKET_SPACE, msg.getBucketSpace());
            }
        }
    }

    public class testGetBucketListMessage implements RunnableTest {

        private static final String BUCKET_SPACE = "beartato";

        @Override
        public void run() {
            GetBucketListMessage msg = new GetBucketListMessage(new BucketId(16, 123));
            msg.setLoadType(loadTypes.getNameMap().get("foo"));
            msg.setBucketSpace(BUCKET_SPACE);
            assertEquals(BASE_MESSAGE_LENGTH + 12 + serializedLength(BUCKET_SPACE), serialize("GetBucketListMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (GetBucketListMessage)deserialize("GetBucketListMessage", DocumentProtocol.MESSAGE_GETBUCKETLIST, lang);
                assertEquals(new BucketId(16, 123), msg.getBucketId());
                assertEquals("foo", msg.getLoadType().getName());
                assertEquals(BUCKET_SPACE, msg.getBucketSpace());
            }
        }
    }

}
