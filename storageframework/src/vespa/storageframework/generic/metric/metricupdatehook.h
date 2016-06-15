// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::MetricUpdateHook
 * \ingroup metric
 *
 * \brief Implement to get callbacks to update metrics periodically or just before reports/snapshots.
 */
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/metrics/metricmanager.h>

namespace storage {
namespace framework {

struct MetricUpdateHook {
    typedef metrics::MetricManager::UpdateHook::MetricLockGuard MetricLockGuard;
    virtual ~MetricUpdateHook() {}

    virtual void updateMetrics(const MetricLockGuard &) = 0;
};

} // framework
} // storage

