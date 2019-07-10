// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mutable_dense_tensor_view.h"

using vespalib::eval::ValueType;

namespace vespalib::tensor {

MutableDenseTensorView::MutableDenseTensorView(ValueType type_in)
    : DenseTensorView(_type),
      _type(type_in)
{
}

}
