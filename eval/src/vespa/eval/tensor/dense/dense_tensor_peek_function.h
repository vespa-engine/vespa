// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::tensor {

/**
 * Tensor function for looking at the cell value of a dense tensor.
 */
class DenseTensorPeekFunction : public eval::TensorFunction
{
private:
    // first child is the tensor we want to peek
    // other children are dimension index expressions
    // (index expressions are sorted by normalized dimension order)
    std::vector<Child> _children;

    // index and size of all dimensions in reverse order
    // when index is -1, use result of next child expression
    // (note that child expression order is inverted by the stack)
    std::vector<std::pair<int64_t,size_t>> _spec;
public:
    DenseTensorPeekFunction(std::vector<Child> children, const std::vector<std::pair<int64_t,size_t>> &spec);
    ~DenseTensorPeekFunction();
    const eval::ValueType &result_type() const override { return eval::DoubleValue::double_type(); }
    void push_children(std::vector<Child::CREF> &children) const override;
    eval::InterpretedFunction::Instruction compile_self(Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::tensor
