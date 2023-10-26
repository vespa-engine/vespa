// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "analyzer.h"

namespace vbench {

/**
 * Deletes incoming request object. This handler is used to terminate
 * a chain of handlers.
 **/
class RequestSink : public Analyzer
{
public:
    RequestSink();
    void handle(Request::UP request) override;
    void report() override;
};

} // namespace vbench
