// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_fast_rename_optimizer.h"
#include "dense_replace_type_function.h"
#include <vespa/eval/eval/value.h>

namespace vespalib::eval {

using namespace tensor_function;

namespace {

bool is_dense_stable_rename(const ValueType &from_type, const ValueType &to_type,
                            const std::vector<vespalib::string> &from,
                            const std::vector<vespalib::string> &to)
{
    if (!from_type.is_dense() ||
        !to_type.is_dense() ||
        (from.size() != to.size()))
    {
        return false;
    }
    size_t npos = ValueType::Dimension::npos;
    for (size_t i = 0; i < from.size(); ++i) {
        size_t old_idx = from_type.dimension_index(from[i]);
        size_t new_idx = to_type.dimension_index(to[i]);
        if ((old_idx != new_idx) || (old_idx == npos)) {
            return false;
        }
    }
    return true;
}

} // namespace vespalib::eval::<unnamed>

const TensorFunction &
DenseFastRenameOptimizer::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto rename = as<Rename>(expr)) {
        const ValueType &from_type = rename->child().result_type();
        const ValueType &to_type = expr.result_type();
        if (is_dense_stable_rename(from_type, to_type, rename->from(), rename->to())) {
            assert(to_type.cell_type() == from_type.cell_type());
            return DenseReplaceTypeFunction::create_compact(to_type, rename->child(), stash);
        }
    }
    return expr;
}

} // namespace vespalib::eval
