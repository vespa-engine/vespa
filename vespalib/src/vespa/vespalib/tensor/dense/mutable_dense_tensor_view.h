// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor_view.h"

namespace vespalib {
namespace tensor {

/**
 * A mutable view to a dense tensor where all dimensions are indexed.
 */
class MutableDenseTensorView : public DenseTensorView
{
    eval::ValueType  _concreteType;

public:
    MutableDenseTensorView(eval::ValueType type_in, CellsRef cells_in)
        : DenseTensorView(_concreteType, cells_in),
          _concreteType(type_in)
    {
    }

    CellsRef &cells() { return _cells; }
    eval::ValueType &type() { return _concreteType; }
};

} // namespace vespalib::tensor
} // namespace vespalib
