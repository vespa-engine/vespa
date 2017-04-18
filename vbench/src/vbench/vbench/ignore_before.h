// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vbench/core/handler.h>
#include "request.h"
#include "analyzer.h"

namespace vbench {

/**
 * Component ignoring (discarding) requests that have start times
 * before a specific time.
 **/
class IgnoreBefore : public Analyzer
{
private:
    Handler<Request> &_next;
    double            _time;
    size_t            _ignored;

public:
    IgnoreBefore(double time, Handler<Request> &next);
    virtual void handle(Request::UP request) override;
    virtual void report() override;
};

} // namespace vbench

