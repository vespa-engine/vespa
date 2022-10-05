// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function that will count the number of cells in the result
 * of a join between two tensors with full mapped overlap consisting
 * of a single dimension.
 **/
class SimpleJoinCount : public tensor_function::Op2
{
private:
    uint64_t _dense_factor;
public:
    SimpleJoinCount(const TensorFunction &lhs_in, const TensorFunction &rhs_in, uint64_t dense_factor_in);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    uint64_t dense_factor() const { return _dense_factor; }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
