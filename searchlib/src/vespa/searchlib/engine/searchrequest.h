// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "propertiesmap.h"
#include "request.h"
#include "lazy_source.h"

namespace search::engine {

class SearchRequest : public Request
{
public:
    using UP = std::unique_ptr<SearchRequest>;
    using SP = std::shared_ptr<SearchRequest>;
    using Source = LazySource<SearchRequest>;

    uint32_t          offset;
    uint32_t          maxhits;
    vespalib::string  sortSpec;
    std::vector<char> groupSpec;
    std::vector<char> sessionId;

    SearchRequest();
    explicit SearchRequest(RelativeTime relativeTime);
    ~SearchRequest() override;
};

}

