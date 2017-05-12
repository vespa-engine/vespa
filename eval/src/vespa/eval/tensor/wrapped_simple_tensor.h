// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor.h"
#include <vespa/eval/eval/simple_tensor.h>

namespace vespalib::tensor {

/**
 * A thin wrapper around a SimpleTensor (tensor reference
 * implementation) to be used as fallback for tensors with data
 * layouts not supported by the default tensor implementation.
 *
 * Tensor implementation class is currently inferred from its value
 * type. Consider adding explicit tagging to the tensor::Tensor
 * default implementation top-level class in the future.
 **/
class WrappedSimpleTensor : public Tensor
{
private:
    std::unique_ptr<eval::SimpleTensor> _space;
    const eval::SimpleTensor &_tensor;
public:
    explicit WrappedSimpleTensor(const eval::SimpleTensor &tensor)
        : _space(), _tensor(tensor) {}
    explicit WrappedSimpleTensor(std::unique_ptr<eval::SimpleTensor> tensor)
        : _space(std::move(tensor)), _tensor(*_space) {}
    ~WrappedSimpleTensor() {}
    const eval::SimpleTensor &get() const { return _tensor; }
    const eval::ValueType &getType() const override { return _tensor.type(); }
    bool equals(const Tensor &arg) const override;
    vespalib::string toString() const override;
    eval::TensorSpec toSpec() const override;
    double sum() const override;
    // functions below should not be used for this implementation
    Tensor::UP add(const Tensor &) const override;
    Tensor::UP subtract(const Tensor &) const override;
    Tensor::UP multiply(const Tensor &) const override;
    Tensor::UP min(const Tensor &) const override;
    Tensor::UP max(const Tensor &) const override;
    Tensor::UP match(const Tensor &) const override;
    Tensor::UP apply(const CellFunction &) const override;
    Tensor::UP sum(const vespalib::string &) const override;
    Tensor::UP apply(const eval::BinaryOperation &, const Tensor &) const override;
    Tensor::UP reduce(const eval::BinaryOperation &, const std::vector<vespalib::string> &) const override;
    void print(std::ostream &out) const override;
    Tensor::UP clone() const override;
    void accept(TensorVisitor &visitor) const override;
};

} // namespace vespalib::tensor
