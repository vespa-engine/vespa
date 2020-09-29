// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_sparse_tensor_builder.h"

namespace vespalib::tensor {

DirectSparseTensorBuilder::DirectSparseTensorBuilder()
    : _type(eval::ValueType::double_type()),
      _index(0),
      _values()
{
}

DirectSparseTensorBuilder::DirectSparseTensorBuilder(const eval::ValueType &type_in)
    : _type(type_in),
      _index(_type.count_mapped_dimensions()),
      _values()
{
}

DirectSparseTensorBuilder::~DirectSparseTensorBuilder() = default;

Tensor::UP
DirectSparseTensorBuilder::build() {
    return std::make_unique<SparseTensor>(std::move(_type), std::move(_index), std::move(_values));
}

void DirectSparseTensorBuilder::reserve(uint32_t estimatedCells) {
    _index.reserve(estimatedCells);
    _values.reserve(estimatedCells);
}

}
