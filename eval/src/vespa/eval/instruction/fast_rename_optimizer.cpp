// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_rename_optimizer.h"
#include "replace_type_function.h"
#include <vespa/eval/eval/value.h>
#include <optional>

namespace vespalib::eval {

using namespace tensor_function;

bool
FastRenameOptimizer::is_stable_rename(const ValueType &from_type, const ValueType &to_type,
                                      const std::vector<vespalib::string> &from,
                                      const std::vector<vespalib::string> &to)
{
    assert(from.size() == to.size());
    auto get_from_idx = [&](const vespalib::string &to_name) {
        for (size_t i = 0; i < to.size(); ++i) {
            if (to[i] == to_name) {
                return from_type.dimension_index(from[i]);
            }
        }
        return from_type.dimension_index(to_name);
    };
    std::optional<size_t> prev_mapped;
    std::optional<size_t> prev_indexed;
    const auto &from_dims = from_type.dimensions();
    for (const auto &to_dim: to_type.dimensions()) {
        size_t from_idx = get_from_idx(to_dim.name);
        assert(from_idx != ValueType::Dimension::npos);
        if (to_dim.is_mapped()) {
            assert(from_dims[from_idx].is_mapped());
            if (prev_mapped && (prev_mapped.value() > from_idx)) {
                return false;
            }
            prev_mapped = from_idx;
        } else if (!to_dim.is_trivial()) {
            assert(from_dims[from_idx].is_indexed());
            if (prev_indexed && (prev_indexed.value() > from_idx)) {
                return false;
            }
            prev_indexed = from_idx;
        }
    }
    return true;
}

const TensorFunction &
FastRenameOptimizer::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto rename = as<Rename>(expr)) {
        const ValueType &from_type = rename->child().result_type();
        const ValueType &to_type = expr.result_type();
        if (is_stable_rename(from_type, to_type, rename->from(), rename->to())) {
            assert(to_type.cell_type() == from_type.cell_type());
            return ReplaceTypeFunction::create_compact(to_type, rename->child(), stash);
        }
    }
    return expr;
}

} // namespace vespalib::eval
