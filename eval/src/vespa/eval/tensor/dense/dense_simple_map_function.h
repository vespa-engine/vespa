// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::tensor {

/**
 * Tensor function for simple map operations on dense tensors.
 **/
class DenseSimpleMapFunction : public eval::tensor_function::Map
{
public:
    using map_fun_t = vespalib::eval::operation::op1_t;
    DenseSimpleMapFunction(const eval::ValueType &result_type,
                           const TensorFunction &child,
                           map_fun_t function_in);
    ~DenseSimpleMapFunction() override;
    bool inplace() const { return child().result_is_mutable(); }
    eval::InterpretedFunction::Instruction compile_self(const eval::TensorEngine &engine, Stash &stash) const override;
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::tensor
