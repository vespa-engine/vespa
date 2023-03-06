// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "component.h"
#include "componentregister.h"
#include <vespa/storageframework/generic/metric/metricregistrator.h>
#include <vespa/storageframework/generic/thread/threadpool.h>
#include <vespa/storageframework/generic/thread/thread.h>

#include <cassert>

namespace storage::framework {

void
Component::open()
{
}

void
Component::close()
{
}

Component::Component(ComponentRegister& cr, vespalib::stringref name)
    : _componentRegister(&cr),
      _name(name),
      _status(nullptr),
      _metric(nullptr),
      _threadPool(nullptr),
      _metricReg(nullptr),
      _clock(nullptr)
{
    cr.registerComponent(*this);
}

Component::~Component() = default;

void
Component::registerStatusPage(const StatusReporter& sr)
{
    assert(_status == nullptr);
    _status = &sr;
}

void
Component::registerMetric(metrics::Metric& m)
{
    assert(_metric == nullptr);
    _metric = &m;
    if (_metricReg != nullptr) {
        _metricReg->registerMetric(m);
    }
}

void
Component::registerMetricUpdateHook(MetricUpdateHook& hook, vespalib::system_time::duration period)
{
    assert(_metricUpdateHook.first == 0);
    _metricUpdateHook = std::make_pair(&hook, period);
    if (_metricReg != nullptr) {
        _metricReg->registerUpdateHook(_name, *_metricUpdateHook.first, _metricUpdateHook.second);
    }
}

void
Component::setMetricRegistrator(MetricRegistrator& mr) {
    _metricReg = &mr;
    if (_metricUpdateHook.first != 0) {
        _metricReg->registerUpdateHook(_name, *_metricUpdateHook.first, _metricUpdateHook.second);
    }
    if (_metric != nullptr) {
        _metricReg->registerMetric(*_metric);
    }
}

ThreadPool&
Component::getThreadPool() const
{
    assert(_threadPool != nullptr);
    return *_threadPool;
}

// Helper functions for components wanting to start a single thread.
std::unique_ptr<Thread>
Component::startThread(Runnable& runnable, vespalib::duration waitTime, vespalib::duration maxProcessTime,
                       int ticksBeforeWait, std::optional<vespalib::CpuUsage::Category> cpu_category) const
{
    return getThreadPool().startThread(runnable, getName(), waitTime,
                                       maxProcessTime, ticksBeforeWait, cpu_category);
}

void
Component::requestShutdown(vespalib::stringref reason)
{
    _componentRegister->requestShutdown(reason);
}

}
