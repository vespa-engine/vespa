// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_modify.h"
#include "dense_tensor_address_mapper.h"
#include "dense_tensor.h"

namespace vespalib::tensor {

DenseTensorModify::DenseTensorModify(join_fun_t op, const eval::ValueType &type, Cells cells)
    : _op(op),
      _type(type),
      _cells(std::move(cells))
{
}
    
DenseTensorModify::~DenseTensorModify() = default;

void
DenseTensorModify::visit(const TensorAddress &address, double value)
{
    uint32_t idx = DenseTensorAddressMapper::mapAddressToIndex(address, _type);
    if (idx != DenseTensorAddressMapper::BAD_ADDRESS) {
        _cells[idx] = _op(_cells[idx], value);
    }
}

std::unique_ptr<Tensor>
DenseTensorModify::build()
{
    return std::make_unique<DenseTensor>(std::move(_type), std::move(_cells));
}

}
