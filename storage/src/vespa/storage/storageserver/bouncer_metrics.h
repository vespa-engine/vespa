// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/countmetric.h>

namespace storage {

struct BouncerMetrics : metrics::MetricSet {
    metrics::LongCountMetric clock_skew_aborts;
    metrics::LongCountMetric unavailable_node_aborts;

    BouncerMetrics();
    ~BouncerMetrics() override;
};

}