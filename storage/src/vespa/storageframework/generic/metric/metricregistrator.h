// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::MetricRegistrator
 * \ingroup metric
 *
 * \brief Interface used to register a metric in the backend.
 *
 * To avoid needing the framework module to depend on the metric system (in
 * case any users don't use it), this class exist to remove this dependency.
 */
#pragma once

#include <vespa/vespalib/util/time.h>

namespace metrics {
    class Metric;
}

namespace storage::framework {

struct MetricUpdateHook;

struct MetricRegistrator {
    virtual ~MetricRegistrator() = default;

    virtual void registerMetric(metrics::Metric&) = 0;
    virtual void registerUpdateHook(vespalib::stringref name, MetricUpdateHook& hook, vespalib::system_time::duration period) = 0;
};

}

