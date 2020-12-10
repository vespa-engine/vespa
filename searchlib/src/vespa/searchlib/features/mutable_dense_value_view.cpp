// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mutable_dense_value_view.h"

namespace search::features {

MutableDenseValueView::MutableDenseValueView(const vespalib::eval::ValueType &type_in)
  : _type(type_in),
    _cells()
{
    assert(_type.is_dense());
}

}
