// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "request.h"
#include <vbench/core/handler.h>

namespace vbench {

struct Analyzer : public Handler<Request>
{
    using UP = std::unique_ptr<Analyzer>;
    virtual void report() = 0;
    virtual ~Analyzer() {}
};

} // namespace vbench
