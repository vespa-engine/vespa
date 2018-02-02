// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;
import com.yahoo.document.DocumentId;
import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.vespa.objects.Deserializer;

import java.util.Map;

/**
 * This class encapsulates all the {@link RoutableFactory} classes needed to implement serialization for the document
 * protocol. When adding new factories to this class, please KEEP THE THEM ORDERED alphabetically like they are now.
 *
 */
public abstract class RoutableFactories51 extends RoutableFactories50 {

    public static class CreateVisitorMessageFactory extends DocumentMessageFactory {

        protected String decodeBucketSpace(Deserializer deserializer) {
            return FixedBucketSpaces.defaultSpace();
        }

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            CreateVisitorMessage msg = new CreateVisitorMessage();
            msg.setLibraryName(decodeString(buf));
            msg.setInstanceId(decodeString(buf));
            msg.setControlDestination(decodeString(buf));
            msg.setDataDestination(decodeString(buf));
            msg.setDocumentSelection(decodeString(buf));
            msg.setMaxPendingReplyCount(buf.getInt(null));

            int size = buf.getInt(null);
            for (int i = 0; i < size; i++) {
                long reversed = buf.getLong(null);
                long rawid = ((reversed >>> 56) & 0x00000000000000FFl) | ((reversed >>> 40) & 0x000000000000FF00l) |
                             ((reversed >>> 24) & 0x0000000000FF0000l) | ((reversed >>> 8) & 0x00000000FF000000l) |
                             ((reversed << 8) & 0x000000FF00000000l) | ((reversed << 24) & 0x0000FF0000000000l) |
                             ((reversed << 40) & 0x00FF000000000000l) | ((reversed << 56) & 0xFF00000000000000l);
                msg.getBuckets().add(new BucketId(rawid));
            }

            msg.setFromTimestamp(buf.getLong(null));
            msg.setToTimestamp(buf.getLong(null));
            msg.setVisitRemoves(buf.getByte(null) == (byte)1);
            msg.setFieldSet(decodeString(buf));
            msg.setVisitInconsistentBuckets(buf.getByte(null) == (byte)1);

            size = buf.getInt(null);
            for (int i = 0; i < size; i++) {
                String key = decodeString(buf);
                int sz = buf.getInt(null);
                msg.getParameters().put(key, buf.getBytes(null, sz));
            }

            msg.setVisitorOrdering(buf.getInt(null));
            msg.setMaxBucketsPerVisitor(buf.getInt(null));
            msg.setVisitorDispatcherVersion(50);
            msg.setBucketSpace(decodeBucketSpace(buf));
            return msg;
        }

        protected boolean encodeBucketSpace(String bucketSpace, DocumentSerializer buf) {
            return FixedBucketSpaces.defaultSpace().equals(bucketSpace);
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            CreateVisitorMessage msg = (CreateVisitorMessage)obj;
            encodeString(msg.getLibraryName(), buf);
            encodeString(msg.getInstanceId(), buf);
            encodeString(msg.getControlDestination(), buf);
            encodeString(msg.getDataDestination(), buf);
            encodeString(msg.getDocumentSelection(), buf);
            buf.putInt(null, msg.getMaxPendingReplyCount());

            buf.putInt(null, msg.getBuckets().size());
            for (BucketId id : msg.getBuckets()) {
                long rawid = id.getRawId();
                long reversed = ((rawid >>> 56) & 0x00000000000000FFl) | ((rawid >>> 40) & 0x000000000000FF00l) |
                                ((rawid >>> 24) & 0x0000000000FF0000l) | ((rawid >>> 8) & 0x00000000FF000000l) |
                                ((rawid << 8) & 0x000000FF00000000l) | ((rawid << 24) & 0x0000FF0000000000l) |
                                ((rawid << 40) & 0x00FF000000000000l) | ((rawid << 56) & 0xFF00000000000000l);
                buf.putLong(null, reversed);
            }

            buf.putLong(null, msg.getFromTimestamp());
            buf.putLong(null, msg.getToTimestamp());
            buf.putByte(null, msg.getVisitRemoves() ? (byte)1 : (byte)0);
            encodeString(msg.getFieldSet(), buf);
            buf.putByte(null, msg.getVisitInconsistentBuckets() ? (byte)1 : (byte)0);

            buf.putInt(null, msg.getParameters().size());
            for (Map.Entry<String, byte[]> pairs : msg.getParameters().entrySet()) {
                encodeString(pairs.getKey(), buf);
                byte[] b = pairs.getValue();
                buf.putInt(null, b.length);
                buf.put(null, b);
            }

            buf.putInt(null, msg.getVisitorOrdering());
            buf.putInt(null, msg.getMaxBucketsPerVisitor());
            return encodeBucketSpace(msg.getBucketSpace(), buf);
        }
    }

    public static class GetDocumentMessageFactory extends DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            return new GetDocumentMessage(new DocumentId(buf), decodeString(buf));
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            GetDocumentMessage msg = (GetDocumentMessage)obj;
            msg.getDocumentId().serialize(buf);
            encodeString(msg.getFieldSet(), buf);
            return true;
        }
    }

    public static class DocumentIgnoredReplyFactory extends DocumentReplyFactory {
        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            return new DocumentIgnoredReply();
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            return true;
        }
    }
}
