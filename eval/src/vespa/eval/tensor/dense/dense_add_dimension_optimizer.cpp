// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_add_dimension_optimizer.h"
#include "dense_replace_type_function.h"
#include <vespa/eval/eval/operation.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.dense.add_dimension_optimizer");

namespace vespalib::tensor {

using eval::ValueType;
using eval::TensorFunction;
using eval::as;
using namespace eval::tensor_function;
using namespace eval::operation;

namespace {

bool same_cell_type(const TensorFunction &a, const TensorFunction &b) {
    return (a.result_type().cell_type() == b.result_type().cell_type());
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
            lhs.result_type().is_dense() &&
            rhs.result_type().is_dense())
        {
            if (is_unit_constant(lhs) && same_cell_type(rhs, expr)) {
                return DenseReplaceTypeFunction::create_compact(expr.result_type(), rhs, stash);
            }
            if (is_unit_constant(rhs) && same_cell_type(lhs, expr)) {
                 return DenseReplaceTypeFunction::create_compact(expr.result_type(), lhs, stash);
            }
        }
    }
    return expr;
}

} // namespace vespalib::tensor
