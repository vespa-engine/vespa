// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vdslib/container/searchresult.h>
#include <vespa/documentapi/messagebus/messages/visitor.h>

namespace documentapi {

class SearchResultMessage : public VisitorMessage,
                            public vdslib::SearchResult {
protected:
    // Implements VisitorMessage.
    DocumentReply::UP doCreateReply() const;

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

    // Overrides VisitorMessage.
    uint32_t getApproxSize() const;

    // Implements VisitorMessage.
    uint32_t getType() const;

    string toString() const { return "searchresultmessage"; }
};

}

