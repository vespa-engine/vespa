// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_lambda_peek_optimizer.h"

namespace vespalib::tensor {

const eval::TensorFunction &
DenseLambdaPeekOptimizer::optimize(const eval::TensorFunction &expr, Stash &)
{
    return expr;
}

}
