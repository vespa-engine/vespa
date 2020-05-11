// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_sampler_functor.h"
#include "attribute_usage_sampler_context.h"
#include "attribute_config_inspector.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/loadedenumvalue.h>
#include <vespa/searchlib/attribute/loadedvalue.h>

using search::attribute::BasicType;

namespace proton {

namespace {

size_t
get_transient_memory_usage(const search::attribute::Config& old_config,
                           const search::attribute::Config& current_config,
                           uint64_t total_value_count)
{
    if (current_config.fastSearch()) {
        if (old_config.fastSearch()) {
            return sizeof(search::attribute::LoadedEnumAttribute) * total_value_count;
        } else {
            switch (old_config.basicType().type()) {
            case BasicType::Type::INT8:
                return sizeof(search::attribute::LoadedValue<int8_t>) * total_value_count;
            case BasicType::Type::INT16:
                return sizeof(search::attribute::LoadedValue<int16_t>) * total_value_count;
            case BasicType::Type::INT32:
                return sizeof(search::attribute::LoadedValue<int32_t>) * total_value_count;
            case BasicType::Type::INT64:
                return sizeof(search::attribute::LoadedValue<int64_t>) * total_value_count;
            case BasicType::Type::FLOAT:
                return sizeof(search::attribute::LoadedValue<float>) * total_value_count;
            case BasicType::Type::DOUBLE:
                return sizeof(search::attribute::LoadedValue<double>) * total_value_count;
            default:
                ;
            }
        }
    }
    return 0u;
}

}

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
    size_t transient_memory_usage = 0;
    auto& old_config = attributeVector.getConfig();
    auto* current_config = _samplerContext->get_attribute_config_inspector().get_config(attributeName);
    if (current_config == nullptr) {
        current_config = &old_config;
    }
    uint64_t total_value_count = attributeVector.getStatus().getNumValues();
    transient_memory_usage = get_transient_memory_usage(old_config, *current_config, total_value_count);
    _samplerContext->merge(usage, transient_memory_usage, attributeName, _subDbName);
}

} // namespace proton
