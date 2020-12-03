// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for a dot product between two 1-dimensional dense tensors.
 */
class DenseDotProductFunction : public tensor_function::Op2
{
public:
    DenseDotProductFunction(const TensorFunction &lhs_in,
                            const TensorFunction &rhs_in);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static bool compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs);
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
