// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>

namespace vespalib::eval {

/**
 * Tensor function for mixed weighted sum or optimized select;
 * very similar to MappedLookup when the selector has a single value which equals 1.0
 **/
class MixedWeightedSumFunction : public tensor_function::Op2
{
private:
    const vespalib::string _select_dim;
public:
    MixedWeightedSumFunction(const ValueType &result_type,
                               const TensorFunction &lhs,
                               const TensorFunction &rhs,
                               const vespalib::string &dim);
    ~MixedWeightedSumFunction() override;
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::eval
