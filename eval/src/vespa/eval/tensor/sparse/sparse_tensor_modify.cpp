// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_modify.h"
#include <vespa/eval/tensor/tensor_address_element_iterator.h>

namespace vespalib::tensor {

SparseTensorModify::SparseTensorModify(join_fun_t op, eval::ValueType type, SparseTensorIndex index, std::vector<double> values)
    : _op(op),
      _type(std::move(type)),
      _index(std::move(index)),
      _values(std::move(values)),
      _addressBuilder()
{
}

SparseTensorModify::~SparseTensorModify() = default;

void
SparseTensorModify::visit(const TensorAddress &address, double value)
{
    _addressBuilder.populate(_type, address);
    auto addressRef = _addressBuilder.getAddressRef();
    size_t idx;
    if (_index.lookup_address(addressRef, idx)) {
        _values[idx] = _op(_values[idx], value);
    }
}

std::unique_ptr<Tensor>
SparseTensorModify::build()
{
    return std::make_unique<SparseTensor>(std::move(_type), std::move(_index), std::move(_values));
}

}
