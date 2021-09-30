// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for joining tensors with full sparse overlap.
 */
class SparseFullOverlapJoinFunction : public tensor_function::Join
{
public:
    SparseFullOverlapJoinFunction(const tensor_function::Join &original);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static bool compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs);
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
