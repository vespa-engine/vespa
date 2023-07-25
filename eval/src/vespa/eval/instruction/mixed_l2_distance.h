// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for a squared euclidean distance producing a sparse result.
 **/
class MixedL2Distance : public tensor_function::Op2
{
public:
    MixedL2Distance(const ValueType &result_type, const TensorFunction &mix_in, const TensorFunction &vec_in);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
