// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_add_dimension_optimizer.h"
#include "dense_replace_type_function.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.dense.add_dimension_optimizer");

namespace vespalib::tensor {

using eval::ValueType;
using eval::TensorFunction;
using eval::as;
using namespace eval::tensor_function;
using namespace eval::operation;

namespace {

bool is_concrete_dense_tensor(const ValueType &type) {
    return (type.is_dense() && !type.is_abstract());
}

bool not_overlapping(const ValueType &a, const ValueType &b) {
    size_t npos = ValueType::Dimension::npos;
    for (const auto &dim: b.dimensions()) {
        if (a.dimension_index(dim.name) != npos) {
            return false;
        }
    }
    return true;
}

bool is_unit_constant(const TensorFunction &node) {
    if (auto const_value = as<ConstValue>(node)) {
        for (const auto &dim: node.result_type().dimensions()) {
            if (dim.size != 1) {
                return false;
            }
        }
        return (const_value->value().as_double() == 1.0);
    }
    return false;
}

} // namespace vespalib::tensor::<unnamed>

const TensorFunction &
DenseAddDimensionOptimizer::optimize(const eval::TensorFunction &expr, Stash &stash)
{
    if (auto join = as<Join>(expr)) {
        const TensorFunction &lhs = join->lhs();
        const TensorFunction &rhs = join->rhs();
        if ((join->function() == Mul::f) &&
            is_concrete_dense_tensor(lhs.result_type()) &&
            is_concrete_dense_tensor(rhs.result_type()) &&
            not_overlapping(lhs.result_type(), rhs.result_type()))
        {
            if (is_unit_constant(lhs)) {
                return DenseReplaceTypeFunction::create_compact(expr.result_type(), rhs, stash);
            }
            if (is_unit_constant(rhs)) {
                return DenseReplaceTypeFunction::create_compact(expr.result_type(), lhs, stash);
            }
        }
    }
    return expr;
}

} // namespace vespalib::tensor
