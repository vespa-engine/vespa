// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value_compare.h"
#include <vespa/eval/eval/tensor.h>
#include <vespa/eval/eval/tensor_engine.h>
#include <vespa/eval/eval/value_codec.h>

namespace vespalib::eval {

namespace {

TensorSpec get_spec_from(const Value &value) {
    auto tensor = dynamic_cast<const vespalib::eval::Tensor *>(&value);
    if (tensor) {
        return tensor->engine().to_spec(value);
    } else {
        return spec_from_value(value);
    }
}

} // namespace <unnamed>

bool operator==(const Value &lhs, const Value &rhs)
{
    return get_spec_from(lhs) == get_spec_from(rhs);
}

std::ostream &operator<<(std::ostream &out, const Value &value)
{
    auto spec = get_spec_from(value);
    out << spec.to_string();
    return out;
}

} // namespace vespalib::eval
