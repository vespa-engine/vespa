// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function optimizing in-place map operations on tensors.
 **/
class MixedMapFunction : public tensor_function::Map
{
public:
    using map_fun_t = operation::op1_t;
    MixedMapFunction(const ValueType &result_type,
                     const TensorFunction &child,
                     map_fun_t function_in);
    ~MixedMapFunction() override;
    bool inplace() const { return child().result_is_mutable(); }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::eval
