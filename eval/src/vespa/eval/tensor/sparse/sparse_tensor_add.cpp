// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_add.h"
#include "sparse_tensor_t.h"

namespace vespalib::tensor {

template<typename T>
SparseTensorAdd<T>::SparseTensorAdd(eval::ValueType type, SparseTensorIndex index, std::vector<T> values)
    : _type(std::move(type)),
      _index(std::move(index)),
      _values(std::move(values)),
      _addressBuilder()
{
}

template<typename T>
SparseTensorAdd<T>::~SparseTensorAdd() = default;

template<typename T>
void
SparseTensorAdd<T>::visit(const TensorAddress &address, double value)
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

template<typename T>
std::unique_ptr<Tensor>
SparseTensorAdd<T>::build()
{
    using tt = SparseTensorT<T>;
    return std::make_unique<tt>(std::move(_type), _index, std::move(_values));
}

template class SparseTensorAdd<float>;
template class SparseTensorAdd<double>;

}
