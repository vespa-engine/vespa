// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

public class StatBucketReply extends DocumentReply {

    private String results = "";

    public StatBucketReply() {
        super(DocumentProtocol.REPLY_STATBUCKET);
    }

    public String getResults() {
        return results;
    }

    public void setResults(String result) {
        results = result;
    }
}
