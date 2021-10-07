// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_sampler_functor.h"
#include "attribute_usage_sampler_context.h"
#include "attribute_config_inspector.h"
#include "attribute_transient_memory_calculator.h"
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
    auto& old_config = attributeVector.getConfig();
    auto* current_config = _samplerContext->get_attribute_config_inspector().get_config(attributeName);
    if (current_config == nullptr) {
        current_config = &old_config;
    }
    AttributeTransientMemoryCalculator get_transient_memory_usage;
    size_t transient_memory_usage = get_transient_memory_usage(attributeVector, *current_config);
    _samplerContext->merge(usage, transient_memory_usage, attributeName, _subDbName);
}

} // namespace proton
