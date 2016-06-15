// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributevector.h>
#include <mutex>

namespace proton {

/**
 * Class used to track a set of initialized attribute vectors.
 */
class InitializedAttributesResult
{
private:
    std::vector<search::AttributeVector::SP> _attributes;
    std::mutex _lock;

public:
    InitializedAttributesResult();
    void add(search::AttributeVector::SP attribute);
    const std::vector<search::AttributeVector::SP> &get() const { return _attributes; }
};

} // namespace proton
