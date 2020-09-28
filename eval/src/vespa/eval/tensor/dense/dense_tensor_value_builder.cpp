// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_value_builder.h"

namespace vespalib::tensor {

template<typename T>
DenseTensorValueBuilder<T>::DenseTensorValueBuilder(const eval::ValueType &type,
                                                    size_t subspace_size_in)
  : _type(type), 
    _cells(subspace_size_in)
{
}

template<typename T>
DenseTensorValueBuilder<T>::~DenseTensorValueBuilder() = default;

template class DenseTensorValueBuilder<float>;
template class DenseTensorValueBuilder<double>;

}
