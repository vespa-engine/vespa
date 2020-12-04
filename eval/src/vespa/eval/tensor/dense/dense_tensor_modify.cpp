// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_address_mapper.h"
#include "dense_tensor.h"

namespace vespalib::tensor {

template <class CT>
DenseTensorModify<CT>::DenseTensorModify(join_fun_t op, const eval::ValueType &type, std::vector<CT> &&cells)
    : _op(op),
      _type(type),
      _cells(std::move(cells))
{
    assert(vespalib::eval::check_cell_type<CT>(type.cell_type()));
}

template <class CT>
DenseTensorModify<CT>::~DenseTensorModify() = default;

template <class CT>
void
DenseTensorModify<CT>::visit(const TensorAddress &address, double value)
{
    uint32_t idx = DenseTensorAddressMapper::mapAddressToIndex(address, _type);
    if (idx != DenseTensorAddressMapper::BAD_ADDRESS) {
        double nv = _op(_cells[idx], value);
        _cells[idx] = (CT) nv;
    }
}

template <class CT>
std::unique_ptr<Tensor>
DenseTensorModify<CT>::build()
{
    return std::make_unique<DenseTensor<CT>>(_type, std::move(_cells));
}

template class DenseTensorModify<float>;
template class DenseTensorModify<double>;

} // namespace
