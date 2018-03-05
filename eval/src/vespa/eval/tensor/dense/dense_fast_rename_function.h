// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::tensor {

/**
 * Tensor function for efficient non-transposing rename of a dense
 * tensor.
 **/
class DenseFastRenameFunction : public eval::tensor_function::Op1
{
public:
    DenseFastRenameFunction(const eval::ValueType &result_type,
                            const eval::TensorFunction &child);
    ~DenseFastRenameFunction();
    eval::InterpretedFunction::Instruction compile_self(Stash &stash) const override;
    bool result_is_mutable() const override { return child().result_is_mutable(); }
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::tensor
