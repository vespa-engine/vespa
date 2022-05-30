// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "component.h"
#include "componentregister.h"
#include <vespa/storageframework/generic/metric/metricregistrator.h>
#include <vespa/storageframework/generic/thread/threadpool.h>
#include <cassert>

namespace storage::framework {

void
Component::open()
{
    if (_listener != 0) _listener->onOpen();
}

void
Component::close()
{
    if (_listener != 0) _listener->onClose();
}

Component::Component(ComponentRegister& cr, vespalib::stringref name)
    : _componentRegister(&cr),
      _name(name),
      _status(nullptr),
      _metric(nullptr),
      _threadPool(nullptr),
      _metricReg(nullptr),
      _clock(nullptr),
      _listener(nullptr)
{
    cr.registerComponent(*this);
}

Component::~Component() = default;

void
Component::registerComponentStateListener(ComponentStateListener& l)
{
    assert(_listener == nullptr);
    _listener = &l;
}

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
Component::registerMetricUpdateHook(MetricUpdateHook& hook, SecondTime period)
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
Thread::UP
Component::startThread(Runnable& runnable, vespalib::duration waitTime, vespalib::duration maxProcessTime,
                       int ticksBeforeWait, std::optional<vespalib::CpuUsage::Category> cpu_category)
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
