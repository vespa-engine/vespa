// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pow_as_map_optimizer.h"
#include <vespa/eval/eval/operation.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;

const TensorFunction &
PowAsMapOptimizer::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto join = as<Join>(expr)) {
        const TensorFunction &lhs = join->lhs();
        const TensorFunction &rhs = join->rhs();
        if ((join->function() == Pow::f) &&
            rhs.result_type().is_double())
        {
            if (auto const_value = as<ConstValue>(rhs)) {
                if (const_value->value().as_double() == 2.0) {
                    return map(lhs, Square::f, stash);
                }
                if (const_value->value().as_double() == 3.0) {
                    return map(lhs, Cube::f, stash);
                }
            }
        }
    }
    return expr;
}

} // namespace vespalib::eval
