// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vbench/core/handler.h>

#include "request.h"
#include "tagger.h"

namespace vbench {

/**
 * Sets the start time of requests based on a given qps.
 **/
class QpsTagger : public Tagger
{
private:
    double            _invQps;
    size_t            _count;
    Handler<Request> &_next;

public:
    QpsTagger(double qps, Handler<Request> &next);
    virtual void handle(Request::UP request) override;
};

} // namespace vbench

