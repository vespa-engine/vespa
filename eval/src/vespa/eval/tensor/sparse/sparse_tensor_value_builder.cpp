// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_value_builder.h"

namespace vespalib::tensor {

template <typename T>
ArrayRef<T> 
SparseTensorValueBuilder<T>::add_subspace(const std::vector<vespalib::stringref> &addr)
{
    uint32_t idx = _cells.size();
    _cells.resize(idx + 1);
    _addr_builder.clear();
    for (const auto & label : addr) {
        _addr_builder.add(label);
    }
    auto tmp_ref = _addr_builder.getAddressRef();
    SparseTensorAddressRef ref(tmp_ref, _stash);
    assert(_index.map.find(ref) == _index.map.end());
    _index.map[ref] = idx;
    return ArrayRef<T>(&_cells[idx], 1);
}

template <typename T>
std::unique_ptr<eval::Value>
SparseTensorValueBuilder<T>::build(std::unique_ptr<eval::ValueBuilder<T>>)
{
    // copy cells to stash:
    ConstArrayRef<T> tmp_cells = _cells;
    ConstArrayRef<T> cells_copy = _stash.copy_array<T>(tmp_cells);
    return std::make_unique<SparseTensorValue>(std::move(_type),
                                               std::move(_index),
                                               TypedCells(cells_copy),
                                               std::move(_stash));
}

template class SparseTensorValueBuilder<float>;
template class SparseTensorValueBuilder<double>;

} // namespace
