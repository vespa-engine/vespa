// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function optimizer for converting join expressions on the
 * form 'join(tensor,<small integer constant>,f(x,y)(pow(x,y))' to
 * expressions on the form 'map(tensor,f(x)(x*x...))'.
 **/
struct PowAsMapOptimizer {
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::eval
