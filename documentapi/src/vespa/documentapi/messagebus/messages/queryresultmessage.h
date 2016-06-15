// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vdslib/container/searchresult.h>
#include <vespa/vdslib/container/documentsummary.h>
#include <vespa/documentapi/messagebus/messages/visitor.h>

namespace documentapi {

class QueryResultMessage : public VisitorMessage {
private:
    vdslib::SearchResult    _searchResult;
    vdslib::DocumentSummary _summary;
protected:
    // Implements VisitorMessage.
    DocumentReply::UP doCreateReply() const;

public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<QueryResultMessage> UP;
    typedef std::shared_ptr<QueryResultMessage> SP;

    /**
     * Constructs a new search result message for deserialization.
     */
    QueryResultMessage();

    /**
     * Constructs a new search result message for the given search result.
     *
     * @param result The result to set.
     */
    QueryResultMessage(const vdslib::SearchResult & result, const vdslib::DocumentSummary & summary);

    // Overrides VisitorMessage.
    uint32_t getApproxSize() const;

    // Implements VisitorMessage.
    uint32_t getType() const;

    // Accessors
    const vdslib::SearchResult & getSearchResult() const { return _searchResult; }
    vdslib::SearchResult & getSearchResult() { return _searchResult; }
    const vdslib::DocumentSummary & getDocumentSummary() const { return _summary; }
    vdslib::DocumentSummary & getDocumentSummary() { return _summary; }

    string toString() const { return "queryresultmessage"; }
};

}

