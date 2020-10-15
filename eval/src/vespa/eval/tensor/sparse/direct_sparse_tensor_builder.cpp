// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_sparse_tensor_builder.h"
#include "sparse_tensor_t.h"
#include <type_traits>

namespace vespalib::tensor {

template<typename T>
DirectSparseTensorBuilder<T>::DirectSparseTensorBuilder()
    : _type(eval::ValueType::double_type()),
      _index(0),
      _values()
{
    assert((std::is_same_v<T,double>));
}

template<typename T>
DirectSparseTensorBuilder<T>::DirectSparseTensorBuilder(const eval::ValueType &type_in)
    : _type(type_in),
      _index(_type.count_mapped_dimensions()),
      _values()
{
}

template<typename T>
DirectSparseTensorBuilder<T>::~DirectSparseTensorBuilder() = default;

template<typename T>
std::unique_ptr<SparseTensorT<T>>
DirectSparseTensorBuilder<T>::build() {
    using tt = SparseTensorT<T>;
    return std::make_unique<tt>(std::move(_type), std::move(_index), std::move(_values));
}

template<typename T>
void
DirectSparseTensorBuilder<T>::reserve(uint32_t estimatedCells) {
    _index.reserve(estimatedCells);
    _values.reserve(estimatedCells);
}

template class DirectSparseTensorBuilder<float>;
template class DirectSparseTensorBuilder<double>;

}
