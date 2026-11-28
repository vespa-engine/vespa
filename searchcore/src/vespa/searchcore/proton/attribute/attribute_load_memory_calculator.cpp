// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_load_memory_calculator.h"

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attribute_header.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/loadedenumvalue.h>
#include <vespa/searchlib/attribute/loadedvalue.h>
#include <vespa/searchlib/tensor/direct_tensor_posting_attribute.h>

using proton::initializer::LoadMemoryUsage;
using search::attribute::BasicType;
using search::tensor::DirectTensorPostingAttribute;

namespace proton {

namespace {

bool has_tensor_label_postings(const search::attribute::Config& config) noexcept {
    return config.basicType().type() == BasicType::Type::TENSOR && config.tensor_label_postings();
}

size_t get_transient_memory_usage(bool old_enumerated, bool new_fast_search, const search::attribute::Config& config,
                                  uint64_t total_value_count) noexcept {
    if (has_tensor_label_postings(config)) {
        // Postings are rebuilt from the stored tensors on every load
        return DirectTensorPostingAttribute::rebuild_transient_memory(total_value_count);
    }
    if (new_fast_search) {
        if (old_enumerated) {
            return sizeof(search::attribute::LoadedEnumAttribute) * total_value_count;
        } else {
            switch (config.basicType().type()) {
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
AttributeLoadMemoryCalculator::operator()(const search::AttributeVector& attribute_vector) const noexcept {
    uint64_t total_value_count = attribute_vector.getStatus().getNumValues();
    if (has_tensor_label_postings(attribute_vector.getConfig())) {
        // Rebuilding postings on load uses one temporary entry per subspace
        const auto* posting_attribute = dynamic_cast<const DirectTensorPostingAttribute*>(&attribute_vector);
        total_value_count = (posting_attribute != nullptr) ? posting_attribute->get_total_subspaces() : 0;
    }
    bool        old_enumerated = attribute_vector.getEnumeratedSave();
    bool        new_fast_search = attribute_vector.want_fast_search();
    size_t      memory_usage = attribute_vector.getStatus().get_used_minus_dead_and_onhold();
    const auto& config = attribute_vector.getConfig();
    return LoadMemoryUsage(get_transient_memory_usage(old_enumerated, new_fast_search, config, total_value_count),
                           memory_usage);
}

LoadMemoryUsage
AttributeLoadMemoryCalculator::operator()(const search::attribute::AttributeHeader& old_header,
                                          const search::attribute::Config&          new_config) const noexcept {
    size_t   memory_usage = old_header.get_memory_usage();
    uint64_t total_value_count = old_header.get_total_value_count();
    if (has_tensor_label_postings(new_config)) {
        // Files saved by older versions have no tensor counts; assume no transient memory then.
        total_value_count = old_header.get_total_tensor_subspaces().value_or(0);
    }
    return LoadMemoryUsage(get_transient_memory_usage(old_header.getEnumerated(), new_config.fastSearch(), new_config,
                                                      total_value_count),
                           memory_usage);
};

} // namespace proton
