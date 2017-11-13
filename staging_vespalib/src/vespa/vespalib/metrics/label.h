// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "handle.h"

namespace vespalib {
namespace metrics {

using LabelValue = vespalib::string;

/**
 * Opaque handle representing an uniquely named label.
 **/
class Label : public Handle<Label> {
public:
    explicit Label(size_t id) : Handle<Label>(id) {}
};

} // namespace vespalib::metrics
} // namespace vespalib
