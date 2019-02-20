// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_remove.h"
#include <vespa/eval/tensor/tensor_address_element_iterator.h>

namespace vespalib::tensor {

SparseTensorRemove::SparseTensorRemove(const eval::ValueType &type, Cells &&cells, Stash &&stash)
    : _type(type),
      _cells(std::move(cells)),
      _stash(std::move(stash)),
      _addressBuilder()
{
}

SparseTensorRemove::~SparseTensorRemove() = default;

void
SparseTensorRemove::visit(const TensorAddress &address, double value)
{
    (void) value;
    _addressBuilder.populate(_type, address);
    auto addressRef = _addressBuilder.getAddressRef();
    _cells.erase(addressRef);
}

std::unique_ptr<Tensor>
SparseTensorRemove::build()
{
    return std::make_unique<SparseTensor>(std::move(_type), std::move(_cells), std::move(_stash));
}

}
