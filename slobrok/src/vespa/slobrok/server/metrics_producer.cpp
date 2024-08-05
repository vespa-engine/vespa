// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "metrics_producer.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/fnet/task.h>
#include <vespa/fnet/transport.h>

namespace slobrok {

using namespace std::chrono;

namespace {

[[nodiscard]] constexpr seconds seconds_since_epoch(std::chrono::system_clock::time_point tp) noexcept {
    return duration_cast<seconds>(tp.time_since_epoch());
}

[[nodiscard]] constexpr milliseconds ms_since_epoch(std::chrono::system_clock::time_point tp) noexcept {
    return duration_cast<milliseconds>(tp.time_since_epoch());
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

    ~MetricsSnapshotter() override { Kill(); }
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
    MetricSnapshot(system_clock::time_point prevTime, system_clock::time_point currTime);
    void addCount(const char *name, const char *desc, uint32_t count);

    vespalib::string asString() const {
        return _data.toString();
    }
};

MetricSnapshot::MetricSnapshot(system_clock::time_point prevTime, system_clock::time_point currTime)
    : _data(),
      _metrics(_data.setObject()),
      _snapshot(_metrics.setObject("snapshot")),
      _values(_metrics.setArray("values")),
      _snapLen(static_cast<double>(seconds_since_epoch(currTime).count() - seconds_since_epoch(prevTime).count()))
{
    _snapshot.setLong("from", seconds_since_epoch(prevTime).count());
    _snapshot.setLong("to",   seconds_since_epoch(currTime).count());
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
make_json_snapshot(const RPCHooks::Metrics &prev, const RPCHooks::Metrics &curr,
                   system_clock::time_point prevTime, system_clock::time_point currTime)
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

void emit_prometheus_counter(vespalib::asciistream &out, std::string_view name,
                             std::string_view description, uint64_t value,
                             system_clock::time_point now)
{
    // Prometheus naming conventions state that "_total" should be used for counter metrics.
    out << "# HELP " << name << "_total " << description << '\n';
    out << "# TYPE " << name << "_total counter\n";
    out << name << "_total " << value << ' ' << ms_since_epoch(now).count() << '\n';
}

void emit_prometheus_gauge(vespalib::asciistream &out, std::string_view name,
                           std::string_view description, uint64_t value,
                           system_clock::time_point now)
{
    // Gauge metrics do not appear to have any convention for name suffixes, so emit name verbatim.
    out << "# HELP " << name << ' ' << description << '\n';
    out << "# TYPE " << name << " gauge\n";
    out << name << ' ' << value << ' ' << ms_since_epoch(now).count() << '\n';
}

vespalib::string
make_prometheus_snapshot(const RPCHooks::Metrics &curr, system_clock::time_point now)
{
    vespalib::asciistream out;
    emit_prometheus_counter(out, "slobrok_heartbeats_failed",
                            "count of failed heartbeat requests",
                            curr.heartBeatFails, now);
    emit_prometheus_counter(out, "slobrok_requests_register",
                            "count of register requests received",
                            curr.registerReqs, now);
    emit_prometheus_counter(out, "slobrok_requests_mirror",
                            "count of mirroring requests received",
                            curr.mirrorReqs, now);
    emit_prometheus_counter(out, "slobrok_requests_admin",
                            "count of administrative requests received",
                            curr.adminReqs, now);
    emit_prometheus_gauge(out, "slobrok_missing_consensus",
                          "number of seconds without full consensus with all other brokers",
                          curr.missingConsensusTime, now);
    return out.str();
}

} // namespace <unnamed>


MetricsProducer::MetricsProducer(const RPCHooks &hooks, FNET_Transport &transport)
    : _rpcHooks(hooks),
      _lastMetrics(RPCHooks::Metrics::zero()),
      _producer(),
      _startTime(system_clock::now()),
      _lastSnapshotStart(_startTime),
      _snapshotter(std::make_unique<MetricsSnapshotter>(transport, *this))
{
}

MetricsProducer::~MetricsProducer() = default;

vespalib::string
MetricsProducer::getMetrics(const vespalib::string &consumer, ExpositionFormat format)
{
    return _producer.getMetrics(consumer, format);
}

vespalib::string
MetricsProducer::getTotalMetrics(const vespalib::string &, ExpositionFormat format)
{
    const auto now = system_clock::now();
    RPCHooks::Metrics current = _rpcHooks.getMetrics();
    if (format == ExpositionFormat::Prometheus) {
        return make_prometheus_snapshot(current, now);
    } else {
        RPCHooks::Metrics start = RPCHooks::Metrics::zero();
        return make_json_snapshot(start, current, _startTime, now);
    }
}


void
MetricsProducer::snapshot()
{
    const auto now = system_clock::now();
    RPCHooks::Metrics current = _rpcHooks.getMetrics();
    _producer.setMetrics(make_json_snapshot(_lastMetrics, current, _lastSnapshotStart, now), ExpositionFormat::JSON);
    _producer.setMetrics(make_prometheus_snapshot(current, now), ExpositionFormat::Prometheus);
    _lastMetrics = current;
    _lastSnapshotStart = now;
}


} // namespace slobrok

