// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "componentregisterimpl.h"
#include <vespa/storageframework/storageframework.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/vespalib/util/exceptions.h>

namespace storage::framework::defaultimplementation {

ComponentRegisterImpl::ComponentRegisterImpl()
    : _componentLock(),
      _components(),
      _topMetricSet("vds", "", ""),
      _hooks(),
      _metricManager(nullptr),
      _clock(nullptr),
      _threadPool(nullptr),
      _upgradeFlag(NO_UPGRADE_SPECIAL_HANDLING_ACTIVE),
      _shutdownListener(nullptr)
{ }

ComponentRegisterImpl::~ComponentRegisterImpl() { }

void
ComponentRegisterImpl::registerComponent(ManagedComponent& mc)
{
    vespalib::LockGuard lock(_componentLock);
    _components.push_back(&mc);
    if (_clock != 0) mc.setClock(*_clock);
    if (_threadPool != 0) mc.setThreadPool(*_threadPool);
    if (_metricManager != 0) mc.setMetricRegistrator(*this);
    mc.setUpgradeFlag(_upgradeFlag);
}

void
ComponentRegisterImpl::requestShutdown(vespalib::stringref reason)
{
    vespalib::LockGuard lock(_componentLock);
    if (_shutdownListener != 0) {
        _shutdownListener->requestShutdown(reason);
    }
}

void
ComponentRegisterImpl::setMetricManager(metrics::MetricManager& mm)
{
    std::vector<ManagedComponent*> components;
    {
        vespalib::LockGuard lock(_componentLock);
        assert(_metricManager == 0);
        components = _components;
        _metricManager = &mm;
    }
    {
        metrics::MetricLockGuard lock(mm.getMetricLock());
        mm.registerMetric(lock, _topMetricSet);
    }
    for (uint32_t i=0; i<components.size(); ++i) {
        components[i]->setMetricRegistrator(*this);
    }
}

void
ComponentRegisterImpl::setClock(Clock& c)
{
    vespalib::LockGuard lock(_componentLock);
    assert(_clock == 0);
    _clock = &c;
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setClock(c);
    }
}

void
ComponentRegisterImpl::setThreadPool(ThreadPool& tp)
{
    vespalib::LockGuard lock(_componentLock);
    assert(_threadPool == 0);
    _threadPool = &tp;
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setThreadPool(tp);
    }
}

void
ComponentRegisterImpl::setUpgradeFlag(UpgradeFlags flag)
{
    vespalib::LockGuard lock(_componentLock);
    _upgradeFlag = flag;
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setUpgradeFlag(_upgradeFlag);
    }
}

const StatusReporter*
ComponentRegisterImpl::getStatusReporter(vespalib::stringref id)
{
    vespalib::LockGuard lock(_componentLock);
    for (uint32_t i=0; i<_components.size(); ++i) {
        if (_components[i]->getStatusReporter() != 0
            && _components[i]->getStatusReporter()->getId() == id)
        {
            return _components[i]->getStatusReporter();
        }
    }
    return 0;
}

std::vector<const StatusReporter*>
ComponentRegisterImpl::getStatusReporters()
{
    std::vector<const StatusReporter*> reporters;
    vespalib::LockGuard lock(_componentLock);
    for (uint32_t i=0; i<_components.size(); ++i) {
        if (_components[i]->getStatusReporter() != 0) {
            reporters.push_back(_components[i]->getStatusReporter());
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

        MetricHookWrapper(vespalib::stringref name,
                          MetricUpdateHook& hook)
            : metrics::UpdateHook(name.c_str()),
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
    vespalib::LockGuard lock(_componentLock);
    metrics::UpdateHook::UP hookPtr(new MetricHookWrapper(name, hook));
    _metricManager->addMetricUpdateHook(*hookPtr, period.getTime());
    _hooks.push_back(std::move(hookPtr));
}

metrics::MetricLockGuard
ComponentRegisterImpl::getMetricManagerLock()
{
    return _metricManager->getMetricLock();
}

void
ComponentRegisterImpl::registerShutdownListener(ShutdownListener& listener)
{
    vespalib::LockGuard lock(_componentLock);
    if (_shutdownListener != 0) {
        throw vespalib::IllegalStateException(
                "A shutdown listener is already registered. Add functionality "
                "for having multiple if we need multiple.", VESPA_STRLOC);
    }
    _shutdownListener = &listener;
}

}
