// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value_compare.h"
#include <vespa/eval/eval/engine_or_factory.h>

namespace vespalib::eval {

bool operator==(const Value &lhs, const Value &rhs)
{
    auto engine = EngineOrFactory::get();
    return engine.to_spec(lhs) == engine.to_spec(rhs);
}

std::ostream &operator<<(std::ostream &out, const Value &value)
{
    auto engine = EngineOrFactory::get();
    auto spec = engine.to_spec(value);
    out << spec.to_string();
    return out;
}

} // namespace vespalib::eval
