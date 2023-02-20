// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::ManagedComponent
 * \ingroup component
 *
 * \brief Interface to expose to manager of components.
 *
 * As to not make the functions needed by the component manager exposed to the
 * component implementation, and vice versa, this interface exist to be what
 * the manager is interested in. That way, component implementation can
 * implement that privately, but expose it to the component register.
 */
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace metrics {
    class Metric;
}

namespace storage::framework {

struct StatusReporter;
struct MetricRegistrator;
struct MetricUpdateHook;
struct ThreadPool;
struct Clock;

struct ManagedComponent {
    virtual ~ManagedComponent() = default;

    [[nodiscard]] virtual const vespalib::string& getName() const = 0;
    virtual metrics::Metric* getMetric() = 0;
    virtual const StatusReporter* getStatusReporter() = 0;

    virtual void setMetricRegistrator(MetricRegistrator&) = 0;
    virtual void setClock(Clock&) = 0;
    virtual void setThreadPool(ThreadPool&) = 0;
    virtual void open() = 0;
    virtual void close() = 0;

};

}
