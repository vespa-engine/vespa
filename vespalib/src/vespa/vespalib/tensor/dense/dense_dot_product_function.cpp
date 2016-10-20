// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_dot_product_function.h"
#include <vespa/vespalib/eval/value.h>
#include <vespa/vespalib/tensor/tensor.h>

namespace vespalib {
namespace tensor {

DenseDotProductFunction::DenseDotProductFunction(InjectUP lhsTensor_, InjectUP rhsTensor_)
    : _lhsTensor(std::move(lhsTensor_)),
      _rhsTensor(std::move(rhsTensor_))
{
}

const eval::Value &
DenseDotProductFunction::eval(const Input &input, Stash &stash) const
{
    const eval::Value &lhsValue = input.get_tensor(_lhsTensor->tensor_id);
    const eval::Value &rhsValue = input.get_tensor(_rhsTensor->tensor_id);
    assert(lhsValue.is_tensor());
    assert(rhsValue.is_tensor());
    const Tensor *lhsTensor = dynamic_cast<const Tensor *>(lhsValue.as_tensor());
    const Tensor *rhsTensor = dynamic_cast<const Tensor *>(rhsValue.as_tensor());
    assert(lhsTensor);
    assert(rhsTensor);
    double result;
    if (_lhsTensor->result_type == _rhsTensor->result_type) {
        result = lhsTensor->match(*rhsTensor)->sum();
    } else {
        result = lhsTensor->multiply(*rhsTensor)->sum();
    }
    return stash.create<eval::DoubleValue>(result);
}

} // namespace tensor
} // namespace vespalib
