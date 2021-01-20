// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "add_trivial_dimension_optimizer.h"
#include "replace_type_function.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/wrap_param.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.dense.add_dimension_optimizer");

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;

namespace {

bool same_cell_type(const TensorFunction &a, const TensorFunction &b) {
    return (a.result_type().cell_type() == b.result_type().cell_type());
}

bool is_unit_constant(const TensorFunction &node) {
    if (! node.result_type().is_dense()) {
        return false;
    }
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

} // namespace vespalib::eval::<unnamed>

const TensorFunction &
AddTrivialDimensionOptimizer::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto join = as<Join>(expr)) {
        const TensorFunction &lhs = join->lhs();
        const TensorFunction &rhs = join->rhs();
        if (join->function() == Mul::f) {
            if (is_unit_constant(lhs) && same_cell_type(rhs, expr)) {
                return ReplaceTypeFunction::create_compact(expr.result_type(), rhs, stash);
            }
            if (is_unit_constant(rhs) && same_cell_type(lhs, expr)) {
                 return ReplaceTypeFunction::create_compact(expr.result_type(), lhs, stash);
            }
        }
    }
    return expr;
}

} // namespace vespalib::eval
