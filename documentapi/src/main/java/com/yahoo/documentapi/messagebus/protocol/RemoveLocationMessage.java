// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.*;
import com.yahoo.document.select.BucketSelector;
import java.util.Set;

/**
 * Message (VDS only) to remove an entire location for users using n= or g= schemes.
 * We use a document selection so the user can specify a subset of those documents to be deleted
 * if they wish.
 */
public class RemoveLocationMessage extends DocumentMessage {
    String documentSelection;
    BucketId bucketId;

    public RemoveLocationMessage(String documentSelection) {
        try {
            this.documentSelection = documentSelection;
            BucketSelector bucketSel = new BucketSelector(new BucketIdFactory());
            Set<BucketId> rawBuckets = bucketSel.getBucketList(documentSelection);
            if (rawBuckets == null || rawBuckets.size() != 1) {
                throw new IllegalArgumentException("Document selection for remove location must map to a single location (user or group)");
            } else {
                // There can only be one.
                for (BucketId id : rawBuckets) {
                    bucketId = id;
                }
            }
        } catch (com.yahoo.document.select.parser.ParseException p) {
            throw new IllegalArgumentException(p);
        }
    }

    public String getDocumentSelection() {
        return documentSelection;
    }

    @Override
    public DocumentReply createReply() {
        return new DocumentReply(DocumentProtocol.REPLY_REMOVELOCATION);
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_REMOVELOCATION;
    }

    public BucketId getBucketId() {
        return bucketId;
    }
}
