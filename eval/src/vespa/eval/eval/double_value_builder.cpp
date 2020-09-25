// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "double_value_builder.h"

namespace vespalib::eval {

DoubleValueBuilder::DoubleValueBuilder(const eval::ValueType &type,
                                       size_t num_mapped_in,
                                       size_t subspace_size_in,
                                       size_t)
  : _value(0.0)
{
        assert(type.is_double());
        assert(num_mapped_in == 0);
        assert(subspace_size_in == 1);
}

DoubleValueBuilder::~DoubleValueBuilder() = default;

}
