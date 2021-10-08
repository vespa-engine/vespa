// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.vdslib.SearchResult;

public class SearchResultMessage extends VisitorMessage {

    private SearchResult searchResult = null;

    public SearchResult getResult() {
        return searchResult;
    }

    public void setSearchResult(SearchResult result) {
        searchResult = result;
    }

    @Override
    public DocumentReply createReply() {
        return new VisitorReply(DocumentProtocol.REPLY_SEARCHRESULT);
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_SEARCHRESULT;
    }
}
