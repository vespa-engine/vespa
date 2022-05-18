// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "json_formatter.h"

namespace vespalib {
namespace metrics {

JsonFormatter::JsonFormatter(const Snapshot &snapshot)
    : _data(),
      _top(_data.setObject()),
      _snapLen(snapshot.endTime() - snapshot.startTime())
{
    if (_snapLen < 0.1) {
        _snapLen = 0.1;
    }
    // cosmetics: ordering inside objects
    _data.insert("name");
    _data.insert("dimensions");
    vespalib::slime::Cursor& meta = _top.setObject("snapshot");
    meta.setLong("from", (long)snapshot.startTime());
    meta.setLong("to",   (long)snapshot.endTime());
    handle(snapshot, _top.setArray("values"));
}

void
JsonFormatter::handle(const Snapshot &snapshot, vespalib::slime::Cursor &target)
{
    for (const CounterSnapshot &entry : snapshot.counters()) {
        handle(entry, target.addObject());
    }
    for (const GaugeSnapshot &entry : snapshot.gauges()) {
        handle(entry, target.addObject());
    }
}

void
JsonFormatter::handle(const CounterSnapshot &snapshot, vespalib::slime::Cursor &target)
{
    target.setString("name", snapshot.name());
    // target.setString("description", ?);
    handle(snapshot.point(), target);
    Cursor& inner = target.setObject("values");
    inner.setLong("count", snapshot.count());
    inner.setDouble("rate", snapshot.count() / _snapLen);
}

void
JsonFormatter::handle(const GaugeSnapshot &snapshot, vespalib::slime::Cursor &target)
{
    target.setString("name", snapshot.name());
    // target.setString("description", ?);
    handle(snapshot.point(), target);
    Cursor& inner = target.setObject("values");
    inner.setDouble("average", snapshot.averageValue());
    inner.setDouble("sum", snapshot.sumValue());
    inner.setDouble("min", snapshot.minValue());
    inner.setDouble("max", snapshot.maxValue());
    inner.setDouble("last", snapshot.lastValue());
    inner.setLong("count", snapshot.observedCount());
    inner.setDouble("rate", snapshot.observedCount() / _snapLen);
}

void
JsonFormatter::handle(const PointSnapshot &snapshot, vespalib::slime::Cursor &target)
{
    if (snapshot.dimensions.size() == 0) {
        return;
    }
    Cursor& inner = target.setObject("dimensions");
    for (const DimensionBinding &entry : snapshot.dimensions) {
        inner.setString(entry.dimensionName(), entry.labelValue());
    }
}

} // namespace vespalib::metrics
} // namespace vespalib
