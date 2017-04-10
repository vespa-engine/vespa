// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vbench/core/handler.h>
#include "request.h"
#include "analyzer.h"

namespace vbench {

/**
 * Component calculating the rate of successful requests based on end
 * time.
 **/
class QpsAnalyzer : public Analyzer
{
private:
    Handler<Request> &_next;
    double            _qps;
    size_t            _samples;
    double            _begin;
    size_t            _cnt;

public:
    QpsAnalyzer(Handler<Request> &next);
    virtual void handle(Request::UP request) override;
    virtual void report() override;
    void addEndTime(double end);
};

} // namespace vbench

