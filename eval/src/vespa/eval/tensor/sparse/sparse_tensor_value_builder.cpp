// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_value_builder.h"
#include "sparse_tensor_t.h"

namespace vespalib::tensor {

template <typename T>
ArrayRef<T> 
SparseTensorValueBuilder<T>::add_subspace(const std::vector<vespalib::stringref> &addr)
{
    uint32_t idx = _cells.size();
    _addr_builder.clear();
    for (const auto & label : addr) {
        _addr_builder.add(label);
    }
    auto tmp_ref = _addr_builder.getAddressRef();
    _index.add_address(tmp_ref);
    _cells.push_back(0.0);
    return ArrayRef<T>(&_cells[idx], 1);
}

template <typename T>
std::unique_ptr<eval::Value>
SparseTensorValueBuilder<T>::build(std::unique_ptr<eval::ValueBuilder<T>>)
{
    return std::make_unique<SparseTensorT<T>>(std::move(_type),
                                              std::move(_index),
                                              std::move(_cells));

}

template class SparseTensorValueBuilder<float>;
template class SparseTensorValueBuilder<double>;

} // namespace
