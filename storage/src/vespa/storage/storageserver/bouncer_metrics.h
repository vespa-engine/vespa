// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metrics.h>

namespace storage {

struct BouncerMetrics : metrics::MetricSet {
    metrics::LongCountMetric clock_skew_aborts;

    BouncerMetrics();
    ~BouncerMetrics() override;
};

}