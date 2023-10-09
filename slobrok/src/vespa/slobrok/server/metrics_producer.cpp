// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "metrics_producer.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/fnet/task.h>
#include <vespa/fnet/transport.h>

namespace slobrok {

using namespace std::chrono;

namespace {

time_t
secondsSinceEpoch() {
    return duration_cast<seconds>(system_clock::now().time_since_epoch()).count();
}

class MetricsSnapshotter : public FNET_Task
{
    MetricsProducer &_owner;

    void PerformTask() override {
        _owner.snapshot();
        Schedule(60.0);
    }
public:
    MetricsSnapshotter(FNET_Transport &transport, MetricsProducer &owner)
        :  FNET_Task(transport.GetScheduler()),
           _owner(owner)
    {
        Schedule(60.0);
    }

    ~MetricsSnapshotter() { Kill(); }
};

class MetricSnapshot
{
private:
    vespalib::Slime _data;
    vespalib::slime::Cursor& _metrics;
    vespalib::slime::Cursor& _snapshot;
    vespalib::slime::Cursor& _values;
    double _snapLen;

public:
    MetricSnapshot(uint32_t prevTime, uint32_t currTime);
    void addCount(const char *name, const char *desc, uint32_t count);

    vespalib::string asString() const {
        return _data.toString();
    }
};

MetricSnapshot::MetricSnapshot(uint32_t prevTime, uint32_t currTime)
    : _data(),
      _metrics(_data.setObject()),
      _snapshot(_metrics.setObject("snapshot")),
      _values(_metrics.setArray("values")),
      _snapLen(currTime - prevTime)
{
    _snapshot.setLong("from", prevTime);
    _snapshot.setLong("to",   currTime);
    if (_snapLen < 1.0) {
        _snapLen = 1.0;
    }
}


void
MetricSnapshot::addCount(const char *name, const char *desc, uint32_t count)
{
    using namespace vespalib::slime::convenience;
    Cursor& value = _values.addObject();
    value.setString("name", name);
    value.setString("description", desc);
    Cursor& inner = value.setObject("values");
    inner.setLong("count", count);
    inner.setDouble("rate", count / _snapLen);
}

vespalib::string
makeSnapshot(const RPCHooks::Metrics &prev, const RPCHooks::Metrics &curr,
             uint32_t prevTime, uint32_t currTime)
{
    MetricSnapshot snapshot(prevTime, currTime);
    snapshot.addCount("slobrok.heartbeats.failed",
             "count of failed heartbeat requests",
             curr.heartBeatFails - prev.heartBeatFails);
    snapshot.addCount("slobrok.requests.register",
             "count of register requests received",
             curr.registerReqs - prev.registerReqs);
    snapshot.addCount("slobrok.requests.mirror",
             "count of mirroring requests received",
             curr.mirrorReqs - prev.mirrorReqs);
    snapshot.addCount("slobrok.requests.admin",
             "count of administrative requests received",
             curr.adminReqs - prev.adminReqs);
    snapshot.addCount("slobrok.missing.consensus",
             "number of seconds without full consensus with all other brokers",
             curr.missingConsensusTime);
    return snapshot.asString();
}

} // namespace <unnamed>


MetricsProducer::MetricsProducer(const RPCHooks &hooks,
                                               FNET_Transport &transport)
    : _rpcHooks(hooks),
      _lastMetrics(RPCHooks::Metrics::zero()),
      _producer(),
      _startTime(secondsSinceEpoch()),
      _lastSnapshotStart(_startTime),
      _snapshotter(new MetricsSnapshotter(transport, *this))
{
}

MetricsProducer::~MetricsProducer() = default;

vespalib::string
MetricsProducer::getMetrics(const vespalib::string &consumer)
{
    return _producer.getMetrics(consumer);
}

vespalib::string
MetricsProducer::getTotalMetrics(const vespalib::string &)
{
    uint32_t now = secondsSinceEpoch();
    RPCHooks::Metrics current = _rpcHooks.getMetrics();
    RPCHooks::Metrics start = RPCHooks::Metrics::zero();
    return makeSnapshot(start, current, _startTime, now);
}


void
MetricsProducer::snapshot()
{
    uint32_t now = secondsSinceEpoch();
    RPCHooks::Metrics current = _rpcHooks.getMetrics();
    _producer.setMetrics(makeSnapshot(_lastMetrics, current, _lastSnapshotStart, now));
    _lastMetrics = current;
    _lastSnapshotStart = now;
}


} // namespace slobrok

