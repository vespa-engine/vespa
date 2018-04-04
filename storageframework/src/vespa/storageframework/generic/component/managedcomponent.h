// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include <vespa/storageframework/generic/clock/time.h>
#include <vespa/vespalib/stllike/string.h>

namespace metrics {
    class Metric;
}

namespace storage::framework {

class StatusReporter;
class MetricRegistrator;
class MetricUpdateHook;
class ThreadPool;
class Clock;

/**
 * The upgrade flags can be used to add forward/backward compatability. In most
 * cases, we can hopefully ignore this as next version is compatible. In some
 * cases the new version might need to avoid doing requests the old version
 * can't handle. In rare cases, the older version might have gotten some forward
 * compatability code added which it might need to activate during an upgrade.
 *
 * Note that these flags must be set in an application when an upgrade requiring
 * this is being performed. Upgrade docs should specify this if needed.
 */
enum UpgradeFlags {
        // Indicates we're either not upgrading, or we're upgrading compatible
        // versions so we doesn't need any special handling.
    NO_UPGRADE_SPECIAL_HANDLING_ACTIVE,
        // The cluster is being upgraded to this major version. We might need to
        // send old type of messages to make older nodes understand what we send
    UPGRADING_TO_MAJOR_VERSION,
        // The cluster is being upgraded to this minor version. We might need to
        // send old type of messages to make older nodes understand what we send
    UPGRADING_TO_MINOR_VERSION,
        // The cluster is being upgraded to the next major version. We might
        // need to refrain from using functionality removed in the new version.
    UPGRADING_FROM_MAJOR_VERSION,
        // The cluster is being upgraded to the next minor version. We might
        // need to refrain from using functionality removed in the new version.
    UPGRADING_FROM_MINOR_VERSION
};

struct ManagedComponent {
    virtual ~ManagedComponent() {}

    virtual const vespalib::string& getName() const = 0;
    virtual metrics::Metric* getMetric() = 0;
    virtual std::pair<MetricUpdateHook*, SecondTime> getMetricUpdateHook() = 0;
    virtual const StatusReporter* getStatusReporter() = 0;

    virtual void setMetricRegistrator(MetricRegistrator&) = 0;
    virtual void setClock(Clock&) = 0;
    virtual void setThreadPool(ThreadPool&) = 0;
    virtual void setUpgradeFlag(UpgradeFlags flag) = 0;
    virtual void open() = 0;
    virtual void close() = 0;

};

}
