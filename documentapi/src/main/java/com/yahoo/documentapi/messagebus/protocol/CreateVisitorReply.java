// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;
import com.yahoo.vdslib.VisitorStatistics;

public class CreateVisitorReply extends DocumentReply {

    private BucketId lastBucket;
    private VisitorStatistics statistics = new VisitorStatistics();

    public CreateVisitorReply(int type) {
        super(type);
        lastBucket = new BucketId(Integer.MAX_VALUE);
    }

    public void setLastBucket(BucketId lastBucket) {
        this.lastBucket = lastBucket;
    }

    public BucketId getLastBucket() {
        return lastBucket;
    }

    public void setVisitorStatistics(VisitorStatistics statistics) {
        this.statistics = statistics;
    }

    public VisitorStatistics getVisitorStatistics() {
        return statistics;
    }


}
