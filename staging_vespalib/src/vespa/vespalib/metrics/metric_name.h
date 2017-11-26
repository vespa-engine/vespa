// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "handle.h"

namespace vespalib {
namespace metrics {

/**
 * Opaque handle representing an uniquely named metric.
 **/
class MetricName : public Handle<MetricName> {
public:
    explicit MetricName(size_t id) : Handle<MetricName>(id) {}
};

} // namespace vespalib::metrics
} // namespace vespalib
