// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mutable_dense_value_view.h"

#include <vespa/check_require.h>

namespace search::features::mutable_value {

MutableDenseValueView::MutableDenseValueView(const ValueType& type_in) : _type(type_in), _cells() {
    CHECK(_type.is_dense());
}

} // namespace search::features::mutable_value
