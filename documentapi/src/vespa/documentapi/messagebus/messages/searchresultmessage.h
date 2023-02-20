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
    using UP = std::unique_ptr<SearchResultMessage>;
    using SP = std::shared_ptr<SearchResultMessage>;

    SearchResultMessage();
    SearchResultMessage(vdslib::SearchResult &&result);

    uint32_t getApproxSize() const override;
    uint32_t getType() const override;
    string toString() const override { return "searchresultmessage"; }
};

}
