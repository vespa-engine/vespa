// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "handle.h"

namespace vespalib::metrics {

using DimensionName = vespalib::string;

struct DimensionTag {};

/**
 * Opaque handle representing an uniquely named dimension.
 **/
struct Dimension : Handle<DimensionTag>
{
    explicit Dimension(size_t id) : Handle(id) {}
    static Dimension from_name(const vespalib::string& name);
    const vespalib::string& as_name() const;
};

} // namespace vespalib::metrics
