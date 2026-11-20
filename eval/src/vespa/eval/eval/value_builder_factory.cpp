// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value_builder_factory.h"

#include <vespa/check_require.h>
#include <vespa/vespalib/util/exceptions.h>

#include <algorithm>

namespace vespalib::eval {

namespace {

struct CopyValue {
    template <typename CT>
    static std::unique_ptr<Value> invoke(const Value& value, const ValueType& type,
                                         const ValueBuilderFactory& factory) {
        size_t                 num_mapped = type.count_mapped_dimensions();
        size_t                 dense_size = type.dense_subspace_size();
        const auto&            idx = value.index();
        auto                   input_cells = value.cells().typify<CT>();
        auto                   builder = factory.create_value_builder<CT>(type, num_mapped, dense_size, idx.size());
        std::vector<string_id> addr(num_mapped);
        if (num_mapped == 0) {
            CHECK(idx.size() == 1);
            auto array_ref = builder->add_subspace(addr);
            for (size_t i = 0; i < dense_size; ++i) {
                array_ref[i] = input_cells[i];
            }
        } else {
            auto view = idx.create_view({});
            view->lookup({});
            std::vector<string_id*> addr_fetch;
            addr_fetch.reserve(num_mapped);
            for (auto& label : addr) {
                addr_fetch.push_back(&label);
            }
            size_t subspace_idx;
            while (view->next_result(addr_fetch, subspace_idx)) {
                auto array_ref = builder->add_subspace(addr);
                for (size_t i = 0; i < dense_size; ++i) {
                    array_ref[i] = input_cells[(dense_size * subspace_idx) + i];
                }
            }
        }
        return builder->build(std::move(builder));
    }
};

struct MakeEmptyValue {
    template <typename CT>
    static std::unique_ptr<Value> invoke(const ValueType& type, const ValueBuilderFactory& factory) {
        size_t num_mapped = type.count_mapped_dimensions();
        size_t dense_size = type.dense_subspace_size();
        size_t expected_subspaces = (num_mapped == 0) ? 1 : 0;
        auto   builder = factory.create_value_builder<CT>(type, num_mapped, dense_size, expected_subspaces);
        if (num_mapped == 0) {
            auto array_ref = builder->add_subspace();
            std::fill(array_ref.begin(), array_ref.end(), CT{});
        }
        return builder->build(std::move(builder));
    }
};

} // namespace

std::unique_ptr<Value> ValueBuilderFactory::copy(const Value& value) const {
    const auto& type = value.type();
    return typify_invoke<1, TypifyCellType, CopyValue>(type.cell_type(), value, type, *this);
}

std::unique_ptr<Value> ValueBuilderFactory::make_empty(const ValueType& type) const {
    if (type.is_error()) {
        throw IllegalArgumentException("cannot make empty value of error type", VESPA_STRLOC);
    }
    return typify_invoke<1, TypifyCellType, MakeEmptyValue>(type.cell_type(), type, *this);
}

} // namespace vespalib::eval
