// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "componentregisterimpl.h"
#include <vespa/storageframework/generic/status/statusreporter.h>
#include <vespa/storageframework/generic/metric/metricupdatehook.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>

namespace storage::framework::defaultimplementation {

ComponentRegisterImpl::ComponentRegisterImpl()
    : _componentLock(),
      _components(),
      _topMetricSet("vds", {}, ""),
      _hooks(),
      _metricManager(nullptr),
      _clock(nullptr),
      _threadPool(nullptr),
      _shutdownListener(nullptr)
{ }

ComponentRegisterImpl::~ComponentRegisterImpl() = default;

void
ComponentRegisterImpl::registerComponent(ManagedComponent& mc)
{
    std::lock_guard lock(_componentLock);
    _components.push_back(&mc);
    if (_clock) {
        mc.setClock(*_clock);
    }
    if (_threadPool) {
        mc.setThreadPool(*_threadPool);
    }
    if (_metricManager) {
        mc.setMetricRegistrator(*this);
    }
}

void
ComponentRegisterImpl::requestShutdown(vespalib::stringref reason)
{
    std::lock_guard lock(_componentLock);
    if (_shutdownListener) {
        _shutdownListener->requestShutdown(reason);
    }
}

void
ComponentRegisterImpl::setMetricManager(metrics::MetricManager& mm)
{
    std::vector<ManagedComponent*> components;
    {
        std::lock_guard lock(_componentLock);
        assert(_metricManager == nullptr);
        components = _components;
        _metricManager = &mm;
    }
    {
        metrics::MetricLockGuard lock(mm.getMetricLock());
        mm.registerMetric(lock, _topMetricSet);
    }
    for (auto* component : components) {
        component->setMetricRegistrator(*this);
    }
}

void
ComponentRegisterImpl::setClock(Clock& c)
{
    std::lock_guard lock(_componentLock);
    assert(_clock == nullptr);
    _clock = &c;
    for (auto* component : _components) {
        component->setClock(c);
    }
}

void
ComponentRegisterImpl::setThreadPool(ThreadPool& tp)
{
    std::lock_guard lock(_componentLock);
    assert(_threadPool == nullptr);
    _threadPool = &tp;
    for (auto* component : _components) {
        component->setThreadPool(tp);
    }
}

const StatusReporter*
ComponentRegisterImpl::getStatusReporter(vespalib::stringref id)
{
    std::lock_guard lock(_componentLock);
    for (auto* component : _components) {
        if ((component->getStatusReporter() != nullptr)
            && (component->getStatusReporter()->getId() == id))
        {
            return component->getStatusReporter();
        }
    }
    return nullptr;
}

std::vector<const StatusReporter*>
ComponentRegisterImpl::getStatusReporters()
{
    std::vector<const StatusReporter*> reporters;
    std::lock_guard lock(_componentLock);
    for (auto* component : _components) {
        if (component->getStatusReporter() != nullptr) {
            reporters.emplace_back(component->getStatusReporter());
        }
    }
    return reporters;
}

void
ComponentRegisterImpl::registerMetric(metrics::Metric& m)
{
    metrics::MetricLockGuard lock(_metricManager->getMetricLock());
    _topMetricSet.registerMetric(m);
}

namespace {
    struct MetricHookWrapper : public metrics::UpdateHook {
        MetricUpdateHook& _hook;

        MetricHookWrapper(vespalib::stringref name, MetricUpdateHook& hook, vespalib::system_time::duration period)
            : metrics::UpdateHook(name.data(), period), // Expected to point to static name
              _hook(hook)
        {
        }

        void updateMetrics(const MetricLockGuard & guard) override { _hook.updateMetrics(guard); }
    };
}

void
ComponentRegisterImpl::registerUpdateHook(vespalib::stringref name,
                                          MetricUpdateHook& hook,
                                          vespalib::system_time::duration period)
{
    std::lock_guard lock(_componentLock);
    auto hookPtr = std::make_unique<MetricHookWrapper>(name, hook, period);
    _metricManager->addMetricUpdateHook(*hookPtr);
    _hooks.emplace_back(std::move(hookPtr));
}

void
ComponentRegisterImpl::registerShutdownListener(ShutdownListener& listener)
{
    std::lock_guard lock(_componentLock);
    assert(_shutdownListener == nullptr);
    _shutdownListener = &listener;
}

}
