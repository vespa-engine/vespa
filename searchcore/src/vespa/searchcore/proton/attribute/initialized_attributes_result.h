// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_initializer_result.h"
#include <mutex>
#include <vector>

namespace proton {

/**
 * Class used to track a set of initialized attribute vectors.
 */
class InitializedAttributesResult
{
private:
    std::vector<AttributeInitializerResult> _attributes;
    std::mutex _lock;

public:
    InitializedAttributesResult();
    void add(AttributeInitializerResult attribute);
    const std::vector<AttributeInitializerResult> &get() const { return _attributes; }
};

} // namespace proton
