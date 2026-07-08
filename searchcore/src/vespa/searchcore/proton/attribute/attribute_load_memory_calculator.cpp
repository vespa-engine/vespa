// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_load_memory_calculator.h"

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attribute_header.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/loadedenumvalue.h>
#include <vespa/searchlib/attribute/loadedvalue.h>

using proton::initializer::LoadMemoryUsage;
using search::attribute::BasicType;

namespace proton {

namespace {

size_t get_transient_memory_usage(bool old_enumerated, const search::attribute::Config& new_config,
                                  uint64_t total_value_count) noexcept {
    if (new_config.fastSearch()) {
        if (old_enumerated) {
            return sizeof(search::attribute::LoadedEnumAttribute) * total_value_count;
        } else {
            switch (new_config.basicType().type()) {
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
            default:;
            }
        }
    }
    return 0u;
}

} // namespace

LoadMemoryUsage
AttributeLoadMemoryCalculator::operator()(const search::AttributeVector&   attribute_vector,
                                          const search::attribute::Config& new_config) const noexcept {
    uint64_t total_value_count = attribute_vector.getStatus().getNumValues();
    bool     old_enumerated = attribute_vector.getEnumeratedSave();
    size_t   memory_usage = attribute_vector.getStatus().get_used_minus_dead_and_onhold();
    return LoadMemoryUsage(get_transient_memory_usage(old_enumerated, new_config, total_value_count), memory_usage);
}

LoadMemoryUsage
AttributeLoadMemoryCalculator::operator()(const search::attribute::AttributeHeader& old_header,
                                          const search::attribute::Config&          new_config) const noexcept {
    size_t memory_usage = old_header.get_memory_usage();
    return LoadMemoryUsage(
        get_transient_memory_usage(old_header.getEnumerated(), new_config, old_header.get_total_value_count()),
        memory_usage);
};

} // namespace proton
