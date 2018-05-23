// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespastat;

import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.DocumentId;
import com.yahoo.document.GlobalId;
import com.yahoo.document.select.BucketSelector;
import com.yahoo.document.select.BucketSet;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusSyncSession;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.GetBucketListMessage;
import com.yahoo.documentapi.messagebus.protocol.GetBucketListReply;
import com.yahoo.documentapi.messagebus.protocol.StatBucketMessage;
import com.yahoo.documentapi.messagebus.protocol.StatBucketReply;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.routing.Route;

import java.util.List;

/**
 * This class fetches bucket information from Vespa
 *
 * @author bjorncs
 */
public class BucketStatsRetriever {

    private final BucketIdFactory bucketIdFactory = new BucketIdFactory();
    private final BucketSelector selector = new BucketSelector(bucketIdFactory);

    private final MessageBusSyncSession session;
    private final MessageBusDocumentAccess documentAccess;

    public BucketStatsRetriever(
            DocumentAccessFactory documentAccessFactory,
            String route,
            ShutdownHookRegistrar registrar) {
        registerShutdownHook(registrar);
        this.documentAccess = documentAccessFactory.createDocumentAccess();
        this.session = documentAccess.createSyncSession(new SyncParameters.Builder().build());
        this.session.setRoute(route);
    }

    private void registerShutdownHook(ShutdownHookRegistrar registrar) {
        registrar.registerShutdownHook(() -> {
            try {
                session.destroy();
            } catch (Exception e) {
                // Ignore exception on shutdown
            }
            try {
                documentAccess.shutdown();
            } catch (Exception e) {
                // Ignore exception on shutdown
            }
        });
    }

    public BucketId getBucketIdForType(ClientParameters.SelectionType type, String id) throws BucketStatsException {
        switch (type) {
            case DOCUMENT:
                return bucketIdFactory.getBucketId(new DocumentId(id));
            case BUCKET:
                // The internal parser of BucketID is used since the Java Long.decode cannot handle unsigned longs.
                return new BucketId(String.format("BucketId(%s)", id));
            case GID:
                return convertGidToBucketId(id);
            case USER:
            case GROUP:
                try {
                    BucketSet bucketList = selector.getBucketList(createDocumentSelection(type, id));
                    if (bucketList.size() != 1) {
                        String message = String.format("Document selection must map to only one location. " +
                                "Specified selection matches %d locations.", bucketList.size());
                        throw new BucketStatsException(message);
                    }
                    return bucketList.iterator().next();
                } catch (ParseException e) {
                    throw new BucketStatsException(String.format("Invalid id: %s (%s).", id, e.getMessage()), e);
                }
            default:
                throw new RuntimeException("Unreachable code");
        }
    }

    public String retrieveBucketStats(ClientParameters.SelectionType type, String id, BucketId bucketId, String bucketSpace) throws BucketStatsException {
        String documentSelection = createDocumentSelection(type, id);
        StatBucketMessage msg = new StatBucketMessage(bucketId, bucketSpace, documentSelection);
        StatBucketReply statBucketReply = sendMessage(msg, StatBucketReply.class);
        return statBucketReply.getResults();
    }

    public List<GetBucketListReply.BucketInfo> retrieveBucketList(BucketId bucketId, String bucketSpace) throws BucketStatsException {
        GetBucketListMessage msg = new GetBucketListMessage(bucketId, bucketSpace);
        GetBucketListReply bucketListReply = sendMessage(msg, GetBucketListReply.class);
        return bucketListReply.getBuckets();
    }


    private <T extends Reply> T sendMessage(DocumentMessage msg, Class<T> expectedReply) throws BucketStatsException {
        Reply reply = session.syncSend(msg);
        return validateReply(reply, expectedReply);
    }

    private static <T extends Reply> T validateReply(Reply reply, Class<T> type) throws BucketStatsException {
        if (reply.hasErrors()) {
            throw new BucketStatsException(makeErrorMessage(reply));
        }
        if (!type.isInstance(reply)) {
            throw new BucketStatsException(String.format("Unexpected reply %s: '%s'", reply.getType(), reply.toString()));
        }
        return type.cast(reply);
    }

    private static String makeErrorMessage(Reply reply) {
        StringBuilder b = new StringBuilder();
        b.append("Request failed: \n");
        for (int i = 0; i < reply.getNumErrors(); i++) {
            b.append(String.format("\t %s\n", reply.getError(i)));
        }
        return b.toString();
    }

    private static String createDocumentSelection(ClientParameters.SelectionType type, String id) {
        switch (type) {
            case BUCKET:
                return "true";
            case DOCUMENT:
                return String.format("id=\"%s\"", id);
            case GID:
                return String.format("id.gid=\"gid(%s)\"", id);
            case USER:
                return String.format("id.user=%s", id);
            case GROUP:
                return String.format("id.group=\"%s\"", id);
            default:
                throw new RuntimeException("Unreachable code");
        }
    }

    private static BucketId convertGidToBucketId(String id) throws BucketStatsException {
        if (!id.matches("0x\\p{XDigit}{24}")) {
            throw new BucketStatsException("Invalid gid: " + id);
        }
        String hexWithoutPrefix = id.substring(2);
        return new GlobalId(convertHexStringToByteArray(hexWithoutPrefix)).toBucketId();
    }

    private static byte[] convertHexStringToByteArray(String s) throws BucketStatsException {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int digit1 = Character.digit(s.charAt(i), 16);
            int digit2 = Character.digit(s.charAt(i + 1), 16);
            data[i / 2] = (byte) ((digit1 << 4) + digit2);
        }
        return data;
    }

    public interface ShutdownHookRegistrar {
        void registerShutdownHook(Runnable runnable);
    }
}
