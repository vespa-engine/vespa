// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_sampler_functor.h"

#include "attribute_config_inspector.h"
#include "attribute_load_memory_calculator.h"
#include "attribute_usage_sampler_context.h"

#include <vespa/searchlib/attribute/attributevector.h>

using search::attribute::BasicType;

namespace proton {

AttributeUsageSamplerFunctor::AttributeUsageSamplerFunctor(
    std::shared_ptr<AttributeUsageSamplerContext> samplerContext, AttributeUsageStatsAndLoadInfo::SubDb sub_db,
    const std::string& subDbName)
    : _samplerContext(std::move(samplerContext)), _sub_db(sub_db), _subDbName(subDbName) {
}

AttributeUsageSamplerFunctor::~AttributeUsageSamplerFunctor() = default;

void AttributeUsageSamplerFunctor::operator()(const search::attribute::IAttributeVector& iAttributeVector) {
    // Executed by attribute writer thread
    const auto&                   attributeVector = dynamic_cast<const search::AttributeVector&>(iAttributeVector);
    search::AddressSpaceUsage     usage = attributeVector.getAddressSpaceUsage();
    std::string                   attributeName = attributeVector.getName();
    AttributeLoadMemoryCalculator calc;
    auto                          load_memory_usage = calc(attributeVector);
    _samplerContext->merge(usage, load_memory_usage, _sub_db, attributeName, _subDbName);
}

} // namespace proton
