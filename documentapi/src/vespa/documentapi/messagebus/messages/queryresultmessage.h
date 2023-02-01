// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"
#include <vespa/vdslib/container/searchresult.h>
#include <vespa/vdslib/container/documentsummary.h>

namespace documentapi {

class QueryResultMessage : public VisitorMessage {
private:
    vdslib::SearchResult    _searchResult;
    vdslib::DocumentSummary _summary;
protected:
    DocumentReply::UP doCreateReply() const override;

public:
    /**
     * Convenience typedefs.
     */
    using UP = std::unique_ptr<QueryResultMessage>;
    using SP = std::shared_ptr<QueryResultMessage>;

    /**
     * Constructs a new search result message for deserialization.
     */
    QueryResultMessage();
    ~QueryResultMessage() override;

    /**
     * Constructs a new search result message for the given search result.
     *
     * @param result The result to set.
     */
    QueryResultMessage(vdslib::SearchResult && result, const vdslib::DocumentSummary & summary);

    uint32_t getApproxSize() const override;
    uint32_t getType() const override;

    // Accessors
    const vdslib::SearchResult & getSearchResult() const { return _searchResult; }
    vdslib::SearchResult & getSearchResult() { return _searchResult; }
    const vdslib::DocumentSummary & getDocumentSummary() const { return _summary; }
    vdslib::DocumentSummary & getDocumentSummary() { return _summary; }

    string toString() const override { return "queryresultmessage"; }
};

}
