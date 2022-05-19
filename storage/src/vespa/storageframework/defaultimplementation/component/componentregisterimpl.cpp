// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "componentregisterimpl.h"
#include <vespa/storageframework/storageframework.h>
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
      _upgradeFlag(NO_UPGRADE_SPECIAL_HANDLING_ACTIVE),
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
    mc.setUpgradeFlag(_upgradeFlag);
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
    for (auto* component : _components) {
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

void
ComponentRegisterImpl::setUpgradeFlag(UpgradeFlags flag)
{
    std::lock_guard lock(_componentLock);
    _upgradeFlag = flag;
    for (auto* component : _components) {
        component->setUpgradeFlag(_upgradeFlag);
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

        MetricHookWrapper(vespalib::stringref name, MetricUpdateHook& hook)
            : metrics::UpdateHook(name.data()), // Expected to point to static name
              _hook(hook)
        {
        }

        void updateMetrics(const MetricLockGuard & guard) override { _hook.updateMetrics(guard); }
    };
}

void
ComponentRegisterImpl::registerUpdateHook(vespalib::stringref name,
                                          MetricUpdateHook& hook,
                                          SecondTime period)
{
    std::lock_guard lock(_componentLock);
    auto hookPtr = std::make_unique<MetricHookWrapper>(name, hook);
    _metricManager->addMetricUpdateHook(*hookPtr, period.getTime());
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
