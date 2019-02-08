// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_add.h"

namespace vespalib::tensor {

SparseTensorAdd::SparseTensorAdd(const eval::ValueType &type, Cells &&cells, Stash &&stash)
    : _type(type),
      _cells(std::move(cells)),
      _stash(std::move(stash)),
      _addressBuilder()
{
}

SparseTensorAdd::~SparseTensorAdd() = default;

void
SparseTensorAdd::visit(const TensorAddress &address, double value)
{
    _addressBuilder.populate(_type, address);
    auto addressRef = _addressBuilder.getAddressRef();
    // Make a persistent copy of the tensor address (owned by _stash) as the cell to insert might not already exist.
    auto  persistentAddress = SparseTensorAddressRef(addressRef, _stash);
    _cells[persistentAddress] = value;
}

std::unique_ptr<Tensor>
SparseTensorAdd::build()
{
    return std::make_unique<SparseTensor>(std::move(_type), std::move(_cells), std::move(_stash));
}

}
