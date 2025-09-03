// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "propertiesmap.h"
#include "request.h"
#include "lazy_source.h"

namespace search::query { class Node; }
namespace search::engine {

class SearchRequest : public Request
{
public:
    using UP = std::unique_ptr<SearchRequest>;
    using SP = std::shared_ptr<SearchRequest>;
    using Source = LazySource<SearchRequest>;

    uint32_t          offset;
    uint32_t          maxhits;
    std::string       sortSpec;
    std::vector<char> groupSpec;
    std::unique_ptr<search::query::Node> queryTree;

    SearchRequest();
    explicit SearchRequest(RelativeTime relativeTime);
    ~SearchRequest() override;
};

}
