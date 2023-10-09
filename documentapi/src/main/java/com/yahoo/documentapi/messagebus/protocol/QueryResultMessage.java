// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.vdslib.SearchResult;
import com.yahoo.vdslib.DocumentSummary;

/**
 */
public class QueryResultMessage extends VisitorMessage {

    private SearchResult searchResult = null;
    private DocumentSummary summary = null;

    public SearchResult getResult() {
        return searchResult;
    }

    public DocumentSummary getSummary() {
        return summary;
    }

    public void setSearchResult(SearchResult result) {
        searchResult = result;
    }

    public void setSummary(DocumentSummary summary) {
        this.summary = summary;
    }

    @Override
    public DocumentReply createReply() {
        return new VisitorReply(DocumentProtocol.REPLY_QUERYRESULT);
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_QUERYRESULT;
    }
}
