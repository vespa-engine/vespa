// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value_compare.h"
#include <vespa/eval/eval/value_codec.h>

namespace vespalib::eval {

bool operator==(const Value &lhs, const Value &rhs)
{
    return TensorSpec::from_value(lhs) == TensorSpec::from_value(rhs);
}

std::ostream &operator<<(std::ostream &out, const Value &value)
{
    return out << TensorSpec::from_value(value);
}

} // namespace vespalib::eval
