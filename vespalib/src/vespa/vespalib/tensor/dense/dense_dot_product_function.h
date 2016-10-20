// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/eval/tensor_function.h>

namespace vespalib {
namespace tensor {

/**
 * Tensor function for a dot product between two 1-dimensional dense tensors.
 */
class DenseDotProductFunction : public eval::TensorFunction
{
private:
    using InjectUP = std::unique_ptr<eval::tensor_function::Inject>;

    InjectUP _lhsTensor;
    InjectUP _rhsTensor;

public:
    DenseDotProductFunction(InjectUP lhsTensor_, InjectUP rhsTensor_);
    const eval::tensor_function::Inject &lhsTensor() const { return *_lhsTensor; }
    const eval::tensor_function::Inject &rhsTensor() const { return *_rhsTensor; }
    virtual const eval::Value &eval(const Input &input, Stash &stash) const override;
};

} // namespace tensor
} // namespace vespalib
