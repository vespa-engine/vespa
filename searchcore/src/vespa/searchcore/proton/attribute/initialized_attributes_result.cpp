// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "initialized_attributes_result.h"

using search::AttributeVector;

namespace proton {

InitializedAttributesResult::InitializedAttributesResult()
    : _attributes(),
      _lock()
{}

void
InitializedAttributesResult::add(AttributeInitializerResult attribute)
{
    std::lock_guard<std::mutex> lockGuard(_lock);
    _attributes.push_back(attribute);
}

} // namespace proton
