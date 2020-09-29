// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_remove.h"
#include "sparse_tensor_t.h"
#include <vespa/eval/tensor/tensor_address_element_iterator.h>

namespace vespalib::tensor {

template<typename T> 
SparseTensorRemove<T>::SparseTensorRemove(const SparseTensorT<T> &input)
    : _input(input),
      _map(input.index().get_map()),
      _addressBuilder()
{
}

template<typename T> 
SparseTensorRemove<T>::~SparseTensorRemove() = default;

template<typename T> 
void
SparseTensorRemove<T>::visit(const TensorAddress &address, double)
{
    _addressBuilder.populate(_input.fast_type(), address);
    auto addressRef = _addressBuilder.getAddressRef();
    _map.erase(addressRef);
}

template<typename T>
std::unique_ptr<Tensor>
SparseTensorRemove<T>::build()
{
    SparseTensorIndex new_index(_input.fast_type().count_mapped_dimensions());
    std::vector<T> new_values;
    new_index.reserve(_map.size());
    new_values.reserve(_map.size());
    for (const auto & kv : _map) {
        size_t idx = new_index.lookup_or_add(kv.first);
        assert(idx == new_values.size());
        double v = _input.get_value(kv.second);
        new_values.push_back(v);
    }
    using tt = SparseTensorT<T>;
    return std::make_unique<tt>(_input.fast_type(), std::move(new_index), std::move(new_values));
}

template class SparseTensorRemove<float>;
template class SparseTensorRemove<double>;

}
