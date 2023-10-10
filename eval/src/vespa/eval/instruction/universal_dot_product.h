// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function performing dot product compatible operations
 * (join:mul, reduce:sum) on values of arbitrary complexity.
 * 
 * Note: can evaluate 'anything', but unless 'force' is given; will
 * try to be a bit conservative about when to optimize.
 **/
class UniversalDotProduct : public tensor_function::Op2
{
public:
    UniversalDotProduct(const ValueType &res_type, const TensorFunction &lhs, const TensorFunction &rhs);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    bool forward() const;
    bool distinct() const;
    bool single() const;
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash, bool force);
};

} // namespace
