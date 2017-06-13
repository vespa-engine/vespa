// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mutable_dense_tensor_view.h"

using vespalib::eval::ValueType;

namespace vespalib {
namespace tensor {

MutableDenseTensorView::MutableValueType::MutableValueType(ValueType type_in)
    : _type(type_in)
{
    std::vector<ValueType::Dimension> &dimensions =
            const_cast<std::vector<ValueType::Dimension> &>(_type.dimensions());
    for (auto &dim : dimensions) {
        if (!dim.is_bound()) {
            _unboundDimSizes.emplace_back(&dim.size);
        }
    }
}

MutableDenseTensorView::MutableValueType::~MutableValueType() {}

MutableDenseTensorView::MutableDenseTensorView(ValueType type_in)
    : DenseTensorView(_concreteType.type(), CellsRef()),
      _concreteType(type_in)
{
}

MutableDenseTensorView::MutableDenseTensorView(ValueType type_in, CellsRef cells_in)
    : DenseTensorView(_concreteType.type(), cells_in),
      _concreteType(type_in)
{
}

} // namespace tensor
} // namespace vespalib
