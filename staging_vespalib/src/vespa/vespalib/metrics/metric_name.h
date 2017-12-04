// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "handle.h"

namespace vespalib::metrics {

struct MetricNameTag {};

/**
 * Opaque handle representing an uniquely named metric.
 **/
using MetricName = Handle<MetricNameTag>;

} // namespace vespalib::metrics
