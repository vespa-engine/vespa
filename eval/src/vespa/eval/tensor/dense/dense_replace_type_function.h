// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::tensor {

/**
 * Tensor function for efficient type-only modification of dense
 * tensor.
 **/
class DenseReplaceTypeFunction : public eval::tensor_function::Op1
{
public:
    DenseReplaceTypeFunction(const eval::ValueType &result_type,
                             const eval::TensorFunction &child);
    ~DenseReplaceTypeFunction();
    eval::InterpretedFunction::Instruction compile_self(Stash &stash) const override;
    bool result_is_mutable() const override { return child().result_is_mutable(); }
    static const DenseReplaceTypeFunction &create_compact(const eval::ValueType &result_type,
            const eval::TensorFunction &child,
            Stash &stash);
};

} // namespace vespalib::tensor
