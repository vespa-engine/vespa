// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "request.h"
#include "analyzer.h"
#include <vbench/core/handler.h>

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
    void handle(Request::UP request) override;
    void report() override;
};

} // namespace vbench
