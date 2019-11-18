// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchrequest.h"

namespace search::engine {

SearchRequest::SearchRequest()
    : SearchRequest(RelativeTime(std::make_unique<SteadyClock>())) {}

SearchRequest::SearchRequest(RelativeTime relativeTime)
    : Request(std::move(relativeTime)),
      offset(0),
      maxhits(10),
      sortSpec(),
      groupSpec(),
      sessionId()
{
}

SearchRequest::~SearchRequest() = default;

}
