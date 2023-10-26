// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.BucketId;
import com.yahoo.documentapi.messagebus.protocol.DocumentListEntry;

import java.util.List;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class BucketListVisitorResponse extends VisitorResponse {
    private BucketId bucketId;
    private List<DocumentListEntry> documents;

    public BucketListVisitorResponse(BucketId bucketId, List<DocumentListEntry> documents, AckToken token) {
        super(token);
        this.bucketId = bucketId;
        this.documents = documents;
    }

    public BucketId getBucketId() {
        return bucketId;
    }

    public List<DocumentListEntry> getDocuments() {
        return documents;
    }
}
