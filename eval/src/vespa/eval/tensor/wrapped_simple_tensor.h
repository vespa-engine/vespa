// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    const eval::ValueType &type() const override { return _tensor.type(); }
    bool equals(const Tensor &arg) const override;
    eval::TensorSpec toSpec() const override;
    double as_double() const override;
    void accept(TensorVisitor &visitor) const override;
    Tensor::UP clone() const override;
    // functions below should not be used for this implementation
    Tensor::UP apply(const CellFunction &) const override;
    Tensor::UP join(join_fun_t, const Tensor &) const override;
    Tensor::UP merge(join_fun_t, const Tensor &) const override;
    Tensor::UP reduce(join_fun_t, const std::vector<vespalib::string> &) const override;
    std::unique_ptr<Tensor> modify(join_fun_t, const CellValues &) const override;
    std::unique_ptr<Tensor> add(const Tensor &arg) const override;
    std::unique_ptr<Tensor> remove(const CellValues &) const override;
};

} // namespace vespalib::tensor
