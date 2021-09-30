// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for a concat forming a vector from double values
 * TODO: consider removing this, since the user can write a tensor
 * create expression instead.
 */
class VectorFromDoublesFunction : public TensorFunction
{
public:
    struct Self {
        const ValueType resultType;
        size_t resultSize;
        Self(const ValueType &r, size_t n) : resultType(r), resultSize(n) {}
    };
private:
    Self _self;
    std::vector<Child> _children;
    void add(const TensorFunction &child);
public:
    VectorFromDoublesFunction(std::vector<Child> children, const ValueType &res_type);
    ~VectorFromDoublesFunction();
    const ValueType &result_type() const override { return _self.resultType; }
    void push_children(std::vector<Child::CREF> &children) const override;
    const vespalib::string &dimension() const {
        return _self.resultType.dimensions()[0].name;
    }
    size_t size() const { return _self.resultSize; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::eval
