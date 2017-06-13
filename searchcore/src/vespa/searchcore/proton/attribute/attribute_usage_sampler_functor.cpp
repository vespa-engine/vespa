// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_sampler_functor.h"
#include "attribute_usage_sampler_context.h"
#include <vespa/searchlib/attribute/attributevector.h>

namespace proton {

AttributeUsageSamplerFunctor::AttributeUsageSamplerFunctor(
        std::shared_ptr<AttributeUsageSamplerContext> samplerContext,
        const std::string &subDbName)
    : _samplerContext(samplerContext),
      _subDbName(subDbName)
{
}

AttributeUsageSamplerFunctor::~AttributeUsageSamplerFunctor()
{
}

void
AttributeUsageSamplerFunctor::operator()(const search::AttributeVector &
                                         attributeVector)
{
    // Executed by attribute writer thread
    search::AddressSpaceUsage usage = attributeVector.getAddressSpaceUsage();
    vespalib::string attributeName = attributeVector.getName();
    _samplerContext->merge(usage, attributeName, _subDbName);
}

} // namespace proton
