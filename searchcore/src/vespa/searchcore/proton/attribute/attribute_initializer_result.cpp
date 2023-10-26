// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initializer_result.h"


namespace proton {

AttributeInitializerResult::AttributeInitializerResult(const AttributeVectorSP &attr)
    : _attr(attr)
{
}

AttributeInitializerResult::~AttributeInitializerResult()
{
}

} // namespace proton
