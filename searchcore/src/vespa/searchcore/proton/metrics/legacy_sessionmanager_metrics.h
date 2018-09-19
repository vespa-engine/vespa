// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/countmetric.h>
#include <vespa/metrics/metricset.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>

namespace proton {

/**
 * Legacy metrics for session manager cache.
 * TODO: Remove on Vespa 7
 */
struct LegacySessionManagerMetrics : metrics::MetricSet
{
    metrics::LongCountMetric numInsert;
    metrics::LongCountMetric numPick;
    metrics::LongCountMetric numDropped;
    metrics::LongValueMetric numCached;
    metrics::LongCountMetric numTimedout;

    void update(const proton::matching::SessionManager::Stats &stats);
    LegacySessionManagerMetrics(metrics::MetricSet *parent);
    ~LegacySessionManagerMetrics();
};

}
