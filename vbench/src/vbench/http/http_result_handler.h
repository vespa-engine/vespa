// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/memory.h>

#include <vbench/core/string.h>

namespace vbench {

using Memory = vespalib::Memory;

/**
 * Callback interface that must be implemented in order to use the
 * http client.
 **/
struct HttpResultHandler {
    virtual void handleHeader(const string& name, const string& value) = 0;
    virtual void handleContent(const Memory& data) = 0;
    virtual void handleFailure(const string& reason) = 0;
    virtual ~HttpResultHandler() = default;
};

} // namespace vbench
