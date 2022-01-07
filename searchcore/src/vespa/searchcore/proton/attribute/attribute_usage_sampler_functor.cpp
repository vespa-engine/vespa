// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_sampler_functor.h"
#include "attribute_usage_sampler_context.h"
#include "attribute_config_inspector.h"
#include <vespa/searchlib/attribute/attributevector.h>

using search::attribute::BasicType;

namespace proton {

AttributeUsageSamplerFunctor::AttributeUsageSamplerFunctor(
        std::shared_ptr<AttributeUsageSamplerContext> samplerContext,
        const std::string &subDbName)
    : _samplerContext(samplerContext),
      _subDbName(subDbName)
{
}

AttributeUsageSamplerFunctor::~AttributeUsageSamplerFunctor() = default;

void
AttributeUsageSamplerFunctor::operator()(const search::attribute::IAttributeVector & iAttributeVector)
{
    // Executed by attribute writer thread
    const auto & attributeVector = dynamic_cast<const search::AttributeVector &>(iAttributeVector);
    search::AddressSpaceUsage usage = attributeVector.getAddressSpaceUsage();
    vespalib::string attributeName = attributeVector.getName();
    _samplerContext->merge(usage, attributeName, _subDbName);
}

}
