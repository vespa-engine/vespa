// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::tensor {

/**
 * Tensor function reducing a single dimension of a dense
 * tensor where the result is also a dense tensor.
 **/
class DenseSingleReduceFunction : public eval::tensor_function::Op1
{
private:
    size_t _dim_idx;
    eval::Aggr _aggr;

public:
    DenseSingleReduceFunction(const eval::ValueType &result_type,
                              const eval::TensorFunction &child,
                              size_t dim_idx, eval::Aggr aggr);
    ~DenseSingleReduceFunction() override;
    size_t dim_idx() const { return _dim_idx; }
    eval::Aggr aggr() const { return _aggr; }
    bool result_is_mutable() const override { return true; }
    eval::InterpretedFunction::Instruction compile_self(eval::EngineOrFactory engine, Stash &stash) const override;
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::tensor
