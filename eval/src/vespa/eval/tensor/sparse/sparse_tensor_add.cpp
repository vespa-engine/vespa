// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_add.h"

namespace vespalib::tensor {

SparseTensorAdd::SparseTensorAdd(eval::ValueType type, SparseTensorIndex index, std::vector<double> values)
    : _type(std::move(type)),
      _index(std::move(index)),
      _values(std::move(values)),
      _addressBuilder()
{
}

SparseTensorAdd::~SparseTensorAdd() = default;

void
SparseTensorAdd::visit(const TensorAddress &address, double value)
{
    _addressBuilder.populate(_type, address);
    auto addressRef = _addressBuilder.getAddressRef();
    size_t idx = _index.lookup_or_add(addressRef);
    if (idx < _values.size()) {
        _values[idx] = value;
    } else {
        assert(idx == _values.size());
        _values.push_back(value);
    }
}

std::unique_ptr<Tensor>
SparseTensorAdd::build()
{
    return std::make_unique<SparseTensor>(std::move(_type), _index, std::move(_values));
}

}
