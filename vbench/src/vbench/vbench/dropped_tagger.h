// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "request.h"
#include <vbench/core/handler.h>

namespace vbench {

/**
 * Tags a request as dropped before passing it along.
 **/
class DroppedTagger : public Handler<Request>
{
private:
    Handler<Request> &_next;

public:
    DroppedTagger(Handler<Request> &next);
    void handle(Request::UP request) override;
};

} // namespace vbench
