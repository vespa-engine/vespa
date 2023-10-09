// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dropped_tagger.h"

namespace vbench {

DroppedTagger::DroppedTagger(Handler<Request> &next)
    : _next(next)
{
}

void
DroppedTagger::handle(Request::UP request)
{
    request->status(Request::STATUS_DROPPED);
    _next.handle(std::move(request));
}

} // namespace vbench
