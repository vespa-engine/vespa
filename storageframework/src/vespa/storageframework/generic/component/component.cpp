// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "component.h"
#include "componentregister.h"
#include <vespa/storageframework/generic/metric/metricregistrator.h>
#include <vespa/storageframework/generic/thread/threadpool.h>

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
      _status(0),
      _metric(0),
      _threadPool(0),
      _metricReg(0),
      _clock(0),
      _listener(0)
{
    cr.registerComponent(*this);
}

Component::~Component() = default;

void
Component::registerComponentStateListener(ComponentStateListener& l)
{
    assert(_listener == 0);
    _listener = &l;
}

void
Component::registerStatusPage(const StatusReporter& sr)
{
    assert(_status == 0);
    _status = &sr;
}

void
Component::registerMetric(metrics::Metric& m)
{
    assert(_metric == 0);
    _metric = &m;
    if (_metricReg != 0) {
        _metricReg->registerMetric(m);
    }
}

void
Component::registerMetricUpdateHook(MetricUpdateHook& hook, SecondTime period)
{
    assert(_metricUpdateHook.first == 0);
    _metricUpdateHook = std::make_pair(&hook, period);
    if (_metricReg != 0) {
        _metricReg->registerUpdateHook(_name, *_metricUpdateHook.first, _metricUpdateHook.second);
    }
}

vespalib::MonitorGuard
Component::getMetricManagerLock()
{
    if (_metricReg != 0) {
        return _metricReg->getMetricManagerLock();
    } else {
        return vespalib::MonitorGuard();
    }
}

void
Component::setMetricRegistrator(MetricRegistrator& mr) {
    _metricReg = &mr;
    if (_metricUpdateHook.first != 0) {
        _metricReg->registerUpdateHook(_name, *_metricUpdateHook.first, _metricUpdateHook.second);
    }
    if (_metric != 0) {
        _metricReg->registerMetric(*_metric);
    }
}

ThreadPool&
Component::getThreadPool() const
{
    assert(_threadPool != 0);
    return *_threadPool;
}

// Helper functions for components wanting to start a single thread.
Thread::UP
Component::startThread(Runnable& runnable, MilliSecTime waitTime, MilliSecTime maxProcessTime, int ticksBeforeWait)
{
    return getThreadPool().startThread(runnable, getName(), waitTime.getTime(),
                                       maxProcessTime.getTime(), ticksBeforeWait);
}

void
Component::requestShutdown(vespalib::stringref reason)
{
    _componentRegister->requestShutdown(reason);
}

}
