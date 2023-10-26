// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>

namespace vespalib::eval {

bool operator==(const Value &lhs, const Value &rhs);

std::ostream &operator<<(std::ostream &out, const Value &value);

}

