// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>
#include <vespa/eval/eval/value.h>

namespace search::fef {

/**
 * Storage cell for values passed between feature executors in the
 * ranking framework. The union either contains a double value
 * directly (number) or a reference to a polymorphic value stored
 * elsewhere (object).
 **/
union NumberOrObject {
    feature_t                   as_number;
    vespalib::eval::Value::CREF as_object;
    char                        as_bytes[std::max(sizeof(as_number), sizeof(as_object))];
    NumberOrObject() { memset(as_bytes, 0, sizeof(as_bytes)); }
    ~NumberOrObject() {}
};

}
