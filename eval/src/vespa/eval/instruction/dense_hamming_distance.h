// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for a hamming distance producing a scalar result.
 **/
class DenseHammingDistance : public tensor_function::Op2
{
public:
    DenseHammingDistance(const TensorFunction &lhs_child,
                         const TensorFunction &rhs_child);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
