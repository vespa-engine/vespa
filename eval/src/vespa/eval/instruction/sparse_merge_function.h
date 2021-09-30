// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for merging two sparse tensors.
 */
class SparseMergeFunction : public tensor_function::Merge
{
public:
    SparseMergeFunction(const tensor_function::Merge &original);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static bool compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs);
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
