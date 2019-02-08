// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_modify.h"
#include <vespa/eval/tensor/tensor_address_element_iterator.h>

namespace vespalib::tensor {

SparseTensorModify::SparseTensorModify(join_fun_t op, const eval::ValueType &type, Stash &&stash, Cells &&cells)
    : _op(op),
      _type(type),
      _stash(std::move(stash)),
      _cells(std::move(cells)),
      _addressBuilder()
{
}

SparseTensorModify::~SparseTensorModify() = default;

void
SparseTensorModify::visit(const TensorAddress &address, double value)
{
    _addressBuilder.populate(_type, address);
    auto addressRef = _addressBuilder.getAddressRef();
    auto cellItr = _cells.find(addressRef);
    if (cellItr != _cells.end()) {
        cellItr->second = _op(cellItr->second, value);
    }
}

std::unique_ptr<Tensor>
SparseTensorModify::build()
{
    return std::make_unique<SparseTensor>(std::move(_type), std::move(_cells), std::move(_stash));
}

}
