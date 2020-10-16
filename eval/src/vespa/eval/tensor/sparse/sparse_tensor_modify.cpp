// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_modify.h"
#include "sparse_tensor_t.h"
#include <vespa/eval/tensor/tensor_address_element_iterator.h>

namespace vespalib::tensor {

template<typename T>
SparseTensorModify<T>::SparseTensorModify(join_fun_t op, const SparseTensorT<T> &input)
    : _op(op),
      _type(input.fast_type()),
      _index(input.index()),
      _values(input.my_values()),
      _addressBuilder()
{
}

template<typename T>
SparseTensorModify<T>::~SparseTensorModify() = default;

template<typename T>
void
SparseTensorModify<T>::visit(const TensorAddress &address, double value)
{
    _addressBuilder.populate(_type, address);
    auto addressRef = _addressBuilder.getAddressRef();
    size_t idx;
    if (_index.lookup_address(addressRef, idx)) {
        _values[idx] = _op(_values[idx], value);
    }
}

template<typename T>
std::unique_ptr<Tensor>
SparseTensorModify<T>::build()
{
    using tt = SparseTensorT<T>;
    return std::make_unique<tt>(std::move(_type), _index.copy(), std::move(_values));
}

template class SparseTensorModify<float>;
template class SparseTensorModify<double>;

}
