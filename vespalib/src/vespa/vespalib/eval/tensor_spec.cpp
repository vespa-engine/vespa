// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "tensor_spec.h"
#include <iostream>

namespace vespalib {
namespace eval {

std::ostream &operator<<(std::ostream &out, const TensorSpec::Label &label)
{
    if (label.is_indexed()) {
        out << label.index;
    } else {
        out << label.name;
    }
    return out;
}

} // namespace vespalib::eval
} // namespace vespalib
