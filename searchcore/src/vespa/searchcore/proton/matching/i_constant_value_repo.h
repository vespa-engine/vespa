// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_cache/constant_value.h>

namespace proton::matching {

/**
 * Interface for retrieving a named constant rank value to be used by features in the rank framework.
 * If the given value is not found a nullptr should be returned.
 */
struct IConstantValueRepo {
    virtual vespalib::eval::ConstantValue::UP getConstant(const vespalib::string &name) const = 0;
    virtual ~IConstantValueRepo() {}
};

}
