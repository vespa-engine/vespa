// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::MetricUpdateHook
 * \ingroup metric
 *
 * \brief Implement to get callbacks to update metrics periodically or just before reports/snapshots.
 */
#pragma once

#include <mutex>

namespace metrics { class MetricLockGuard; }

namespace storage::framework {

struct MetricUpdateHook {
    using MetricLockGuard = metrics::MetricLockGuard;
    virtual ~MetricUpdateHook() = default;

    virtual void updateMetrics(const MetricLockGuard &) = 0;
};

}
