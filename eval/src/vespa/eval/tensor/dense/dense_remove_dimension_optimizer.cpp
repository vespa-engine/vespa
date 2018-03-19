// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_remove_dimension_optimizer.h"
#include "dense_replace_type_function.h"
#include <vespa/eval/eval/value_type.h>

namespace vespalib::tensor {

using eval::Aggr;
using eval::ValueType;
using eval::TensorFunction;
using eval::as;
using namespace eval::tensor_function;

namespace {

bool is_concrete_dense_tensor(const ValueType &type) {
    return (type.is_dense() && !type.is_abstract());
}

bool is_ident_aggr(Aggr aggr) {
    return ((aggr == Aggr::AVG)  ||
            (aggr == Aggr::PROD) ||
            (aggr == Aggr::SUM)  ||
            (aggr == Aggr::MAX)  ||
            (aggr == Aggr::MIN));
}

bool is_trivial_dim_list(const ValueType &type, const std::vector<vespalib::string> &dim_list) {
    size_t npos = ValueType::Dimension::npos;
    for (const vespalib::string &dim: dim_list) {
        size_t idx = type.dimension_index(dim);
        if ((idx == npos) || (type.dimensions()[idx].size != 1)) {
            return false;
        }
    }
    return true;
}

} // namespace vespalib::tensor::<unnamed>

const TensorFunction &
DenseRemoveDimensionOptimizer::optimize(const eval::TensorFunction &expr, Stash &stash)
{
    if (auto reduce = as<Reduce>(expr)) {
        const TensorFunction &child = reduce->child();
        if (is_concrete_dense_tensor(expr.result_type()) &&
            is_concrete_dense_tensor(child.result_type()) &&
            is_ident_aggr(reduce->aggr()) &&
            is_trivial_dim_list(child.result_type(), reduce->dimensions()))
        {
            return DenseReplaceTypeFunction::create_compact(expr.result_type(), child, stash);
        }
    }
    return expr;
}

} // namespace vespalib::tensor
