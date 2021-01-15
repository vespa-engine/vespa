// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_rename_optimizer.h"
#include "just_replace_type_function.h"
#include <vespa/eval/eval/value.h>

namespace vespalib::eval {

using namespace tensor_function;

namespace {

bool is_ascending(const std::vector<size_t> &values) {
    for (size_t i = 1; i < values.size(); ++i) {
        if (values[i-1] >= values[i]) {
            return false;
        }
    }
    return true;
}

bool is_stable_rename(const ValueType &from_type, const ValueType &to_type,
                      const std::vector<vespalib::string> &from,
                      const std::vector<vespalib::string> &to)
{
    if (from.size() != to.size()) {
        return false;
    }
    size_t npos = ValueType::Dimension::npos;
    std::map<vespalib::string, size_t> name_to_new_idx;
    for (size_t i = 0; i < from.size(); ++i) {
        size_t old_idx = from_type.dimension_index(from[i]);
        size_t new_idx = to_type.dimension_index(to[i]);
        if (old_idx == npos || new_idx == npos) {
            return false;
        }
        auto [iter, inserted] = name_to_new_idx.emplace(from[i], new_idx);
        if (! inserted) {
            abort();
            return false;
        }
    }
    const auto & input_dims = from_type.dimensions();
    std::vector<size_t> sparse_order;
    std::vector<size_t> dense_order;
    size_t old_idx = 0;
    for (const auto & dim : input_dims) {
        size_t new_idx = old_idx++;
        if (name_to_new_idx.count(dim.name) != 0) {
            new_idx = name_to_new_idx[dim.name];
        }
        if (dim.is_mapped()) {
            sparse_order.push_back(new_idx);
        } else if (!dim.is_trivial()) {
            dense_order.push_back(new_idx);
        }
    }
    return (is_ascending(sparse_order) && is_ascending(dense_order));
}

} // namespace vespalib::eval::<unnamed>

const TensorFunction &
FastRenameOptimizer::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto rename = as<Rename>(expr)) {
        const ValueType &from_type = rename->child().result_type();
        const ValueType &to_type = expr.result_type();
        if (is_stable_rename(from_type, to_type, rename->from(), rename->to())) {
            assert(to_type.cell_type() == from_type.cell_type());
            return JustReplaceTypeFunction::create_compact(to_type, rename->child(), stash);
        }
    }
    return expr;
}

} // namespace vespalib::eval
