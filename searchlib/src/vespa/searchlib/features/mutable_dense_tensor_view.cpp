// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mutable_dense_tensor_view.h"

namespace vespalib::eval {

MutableDenseTensorView::MutableDenseTensorView(const ValueType &type_in)
  : _type(type_in),
    _cells()
{
    assert(_type.is_dense());
}

}
