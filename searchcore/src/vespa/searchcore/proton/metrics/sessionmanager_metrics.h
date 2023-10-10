// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/countmetric.h>
#include <vespa/metrics/metricset.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>

namespace proton {

/**
 * Metrics for session manager cache (search or grouping requests).
 */
struct SessionManagerMetrics : metrics::MetricSet
{
    metrics::LongCountMetric numInsert;
    metrics::LongCountMetric numPick;
    metrics::LongCountMetric numDropped;
    metrics::LongValueMetric numCached;
    metrics::LongCountMetric numTimedout;

    void update(const proton::matching::SessionManager::Stats &stats);
    SessionManagerMetrics(const vespalib::string &name, metrics::MetricSet *parent);
    ~SessionManagerMetrics();
};

}
