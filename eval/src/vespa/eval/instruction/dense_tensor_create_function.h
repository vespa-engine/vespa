// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for creating a dense tensor from double values.
 * TODO: benchmark how useful this is, maybe we can just drop it.
 */
class DenseTensorCreateFunction : public TensorFunction
{
public:
    struct Self {
        ValueType result_type;
        size_t result_size;
        Self(const ValueType &r, size_t n) : result_type(r), result_size(n) {}
    };
private:
    Self _self;
    std::vector<Child> _children;
public:
    DenseTensorCreateFunction(const ValueType &res_type, std::vector<Child> children);
    ~DenseTensorCreateFunction();
    const ValueType &result_type() const override { return _self.result_type; }
    void push_children(std::vector<Child::CREF> &children) const override;
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::eval
