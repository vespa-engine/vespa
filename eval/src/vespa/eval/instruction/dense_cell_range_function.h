// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function creating a view to a continuous range of cells in
 * another tensor. The value type will (typically) change, but the
 * cell type must remain the same.
 **/
class DenseCellRangeFunction : public tensor_function::Op1
{
private:
    size_t _offset;
    size_t _length;

public:
    DenseCellRangeFunction(const ValueType &result_type,
                           const TensorFunction &child,
                           size_t offset, size_t length);
    ~DenseCellRangeFunction() override;
    size_t offset() const { return _offset; }
    size_t length() const { return _length; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return child().result_is_mutable(); }
};

} // namespace
