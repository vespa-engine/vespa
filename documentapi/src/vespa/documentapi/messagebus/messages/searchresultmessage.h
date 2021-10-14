// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"
#include <vespa/vdslib/container/searchresult.h>

namespace documentapi {

class SearchResultMessage : public VisitorMessage,
                            public vdslib::SearchResult {
protected:
    DocumentReply::UP doCreateReply() const override;

public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<SearchResultMessage> UP;
    typedef std::shared_ptr<SearchResultMessage> SP;

    /**
     * Constructs a new search result message for deserialization.
     */
    SearchResultMessage();

    /**
     * Constructs a new search result message for the given search result.
     *
     * @param result The result to set.
     */
    SearchResultMessage(const vdslib::SearchResult &result);

    uint32_t getApproxSize() const override;
    uint32_t getType() const override;
    string toString() const override { return "searchresultmessage"; }
};

}

