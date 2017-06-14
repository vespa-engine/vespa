// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>

namespace search {
namespace grouping {

struct SessionManagerMetrics : metrics::MetricSet
{
    metrics::LongCountMetric numInsert;
    metrics::LongCountMetric numPick;
    metrics::LongCountMetric numDropped;
    metrics::LongValueMetric numCached;
    metrics::LongCountMetric numTimedout;

    void update(const proton::matching::SessionManager::Stats &stats);
    SessionManagerMetrics(metrics::MetricSet *parent);
    ~SessionManagerMetrics();
};

}  // namespace grouping
}  // namespace search

