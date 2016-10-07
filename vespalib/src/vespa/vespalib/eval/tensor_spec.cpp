// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/util/stringfmt.h>
#include "tensor_spec.h"
#include <iostream>

namespace vespalib {
namespace eval {

vespalib::string
TensorSpec::to_string() const
{
    vespalib::string out = vespalib::make_string("spec(%s) {\n", _type.c_str());
    for (const auto &cell: _cells) {
        size_t n = 0;
        out.append("  [");
        for (const auto &label: cell.first) {
            if (n++) {
                out.append(",");
            }
            if (label.second.is_mapped()) {
                out.append(label.second.name);
            } else {
                out.append(vespalib::make_string("%zu", label.second.index));
            }
        }
        out.append(vespalib::make_string("]: %g\n", cell.second.value));
    }
    out.append("}");
    return out;
}

bool
operator==(const TensorSpec &lhs, const TensorSpec &rhs)
{
    return ((lhs.type() == rhs.type()) &&
            (lhs.cells() == rhs.cells()));
}

std::ostream &
operator<<(std::ostream &out, const TensorSpec &spec)
{
    out << spec.to_string();
    return out;
}

} // namespace vespalib::eval
} // namespace vespalib
