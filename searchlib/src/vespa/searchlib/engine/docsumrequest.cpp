// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumrequest.h"

namespace search::engine {

DocsumRequest::DocsumRequest()
    : DocsumRequest(RelativeTime(std::make_unique<SteadyClock>()))
{
}

DocsumRequest::DocsumRequest(RelativeTime relativeTime)
    : Request(std::move(relativeTime)),
      resultClassName(),
      hits(),
      sessionId(),
      _fields()
{
}

DocsumRequest::~DocsumRequest() = default;

}
