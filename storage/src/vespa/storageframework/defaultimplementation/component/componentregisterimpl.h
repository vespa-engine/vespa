// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::ComponentRegisterImpl
 * \ingroup component
 *
 * \brief Application server uses this class to manage components.
 *
 * This class implements set functions for the various implementations needed.
 * It will set these implementations in all components already registered, and
 * in components registered after that. Simplifies login in application server
 * as it can just instantiate components in some order and set implementations
 * as soon as they exist.
 *
 * It is possibly to subclass this implementation. That is useful if you also
 * subclass component class to provide extra functionality. Then you can handle
 * that extra functionality in the subclass.
 */
#pragma once

#include <vespa/storageframework/generic/component/componentregister.h>
#include <vespa/storageframework/generic/component/managedcomponent.h>
#include <vespa/storageframework/generic/metric/metricregistrator.h>
#include <vespa/storageframework/generic/status/statusreportermap.h>
#include <vespa/metrics/metricset.h>
#include <mutex>

namespace metrics {

    class MetricManager;
    class UpdateHook;

}


namespace storage::framework::defaultimplementation {

struct ShutdownListener {
    virtual ~ShutdownListener() = default;
    virtual void requestShutdown(vespalib::stringref reason) = 0;
};

class ComponentRegisterImpl : public virtual ComponentRegister,
                              public StatusReporterMap,
                              public MetricRegistrator
{
    std::mutex _componentLock;
    std::vector<ManagedComponent*> _components;

    metrics::MetricSet _topMetricSet;
    std::vector<std::unique_ptr<metrics::UpdateHook>> _hooks;
    metrics::MetricManager* _metricManager;
    Clock* _clock;
    ThreadPool* _threadPool;
    ShutdownListener* _shutdownListener;

public:
    using UP = std::unique_ptr<ComponentRegisterImpl>;

    ComponentRegisterImpl();
    ~ComponentRegisterImpl() override;

    [[nodiscard]] bool hasMetricManager() const { return (_metricManager != nullptr); }
    metrics::MetricManager& getMetricManager() { return *_metricManager; }

    void registerComponent(ManagedComponent&) override;
    void requestShutdown(vespalib::stringref reason) override;

    void setMetricManager(metrics::MetricManager&);
    void setClock(Clock&);
    void setThreadPool(ThreadPool&);

    const StatusReporter* getStatusReporter(vespalib::stringref id) override;
    std::vector<const StatusReporter*> getStatusReporters() override;

    void registerMetric(metrics::Metric&) override;
    void registerUpdateHook(vespalib::stringref name, MetricUpdateHook& hook, vespalib::system_time::duration period) override;
    void registerShutdownListener(ShutdownListener&);

};

}
