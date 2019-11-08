// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::tensor {

/**
 * Tensor function for creating a dense tensor from double values.
 */
class DenseTensorCreateFunction : public eval::TensorFunction
{
public:
    struct Self {
        eval::ValueType result_type;
        size_t result_size;
        Self(const eval::ValueType &r, size_t n) : result_type(r), result_size(n) {}
    };
private:
    Self _self;
    std::vector<Child> _children;
public:
    DenseTensorCreateFunction(const eval::ValueType &res_type, std::vector<Child> children);
    ~DenseTensorCreateFunction();
    const eval::ValueType &result_type() const override { return _self.result_type; }
    void push_children(std::vector<Child::CREF> &children) const override;
    eval::InterpretedFunction::Instruction compile_self(Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::tensor
