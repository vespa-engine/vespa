// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumrequest.h"

namespace search::engine {

DocsumRequest::DocsumRequest()
    : DocsumRequest(false)
{}

DocsumRequest::DocsumRequest(bool useRootSlime_)
    : DocsumRequest(RelativeTime(std::make_unique<SteadyClock>()), useRootSlime_)
{}

DocsumRequest::DocsumRequest(RelativeTime relativeTime, bool useRootSlime_)
    : Request(std::move(relativeTime)),
      resultClassName(),
      _useRootSlime(useRootSlime_),
      hits()
{
}

DocsumRequest::~DocsumRequest() = default;

}
