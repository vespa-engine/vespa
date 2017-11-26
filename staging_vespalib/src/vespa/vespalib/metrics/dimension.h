// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "handle.h"

namespace vespalib {
namespace metrics {

using DimensionName = vespalib::string;

/**
 * Opaque handle representing an uniquely named dimension.
 **/
class Dimension : public Handle<Dimension> {
public:
    explicit Dimension(size_t id) : Handle<Dimension>(id) {}
};

} // namespace vespalib::metrics
} // namespace vespalib
