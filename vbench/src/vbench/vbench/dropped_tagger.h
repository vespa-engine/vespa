// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vbench/core/handler.h>

#include "request.h"

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
    virtual void handle(Request::UP request) override;
};

} // namespace vbench

