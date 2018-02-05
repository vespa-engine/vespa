// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::tensor {

/**
 * Tensor function for a concat forming a vector from double values
 */
class VectorFromDoublesFunction : public eval::TensorFunction
{
public:
    struct Self {
        const eval::ValueType resultType;
        size_t resultSize;
        Self(const eval::ValueType &r, size_t n) : resultType(r), resultSize(n) {}
    };
private:
    Self _self;
    std::vector<Child> _children;
    void add(const eval::TensorFunction &child);
public:
    VectorFromDoublesFunction(std::vector<Child> children, const eval::ValueType &res_type);
    ~VectorFromDoublesFunction();
    const eval::ValueType &result_type() const override { return _self.resultType; }
    void push_children(std::vector<Child::CREF> &children) const override;
    const vespalib::string &dimension() const {
        return _self.resultType.dimensions()[0].name;
    }
    size_t size() const { return _self.resultSize; }
    eval::InterpretedFunction::Instruction compile_self(Stash &stash) const override;
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::tensor
