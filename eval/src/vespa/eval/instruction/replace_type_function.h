// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for efficient type-only modification of values.
 **/
class ReplaceTypeFunction : public tensor_function::Op1
{
public:
    ReplaceTypeFunction(const ValueType &result_type,
                        const TensorFunction &child);
    ~ReplaceTypeFunction();
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return child().result_is_mutable(); }
    static const ReplaceTypeFunction &create_compact(const ValueType &result_type,
                                                     const TensorFunction &child,
                                                     Stash &stash);
};

} // namespace vespalib::eval
