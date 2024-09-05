// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "handle.h"
#include <string>

namespace vespalib::metrics {

using DimensionName = std::string;

struct DimensionTag {};

/**
 * Opaque handle representing an uniquely named dimension.
 **/
struct Dimension : Handle<DimensionTag>
{
    explicit Dimension(size_t id) : Handle(id) {}
    static Dimension from_name(const std::string& name);
    const std::string& as_name() const;
};

} // namespace vespalib::metrics
