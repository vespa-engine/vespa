// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class VisitorInfoMessage extends VisitorMessage {

    private Set<BucketId> finishedBuckets = new TreeSet<BucketId>();
    private String errorMessage = "";

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String s) {
        errorMessage = s;
    }

    public Set<BucketId> getFinishedBuckets() {
        return finishedBuckets;
    }

    public void setFinishedBuckets(Set<BucketId> finishedBuckets) {
        this.finishedBuckets = finishedBuckets;
    }

    @Override
    public DocumentReply createReply() {
        return new VisitorReply(DocumentProtocol.REPLY_VISITORINFO);
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_VISITORINFO;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("VisitorInfoMessage(");
        if (finishedBuckets.size() == 0) {
            sb.append("No buckets");
        } else if (finishedBuckets.size() == 1) {
            sb.append("Bucket ").append(finishedBuckets.iterator().next().toString());
        } else if (finishedBuckets.size() < 65536) {
            sb.append(finishedBuckets.size()).append(" buckets:");
            Iterator<BucketId> it = finishedBuckets.iterator();
            for (int i = 0; it.hasNext() && i < 3; ++i) {
                sb.append(' ').append(it.next().toString());
            }
            if (it.hasNext()) {
                sb.append(" ...");
            }
        } else {
            sb.append("All buckets");
        }
        sb.append(", error message '").append(errorMessage).append('\'');
        return sb.append(')').toString();
    }
}
