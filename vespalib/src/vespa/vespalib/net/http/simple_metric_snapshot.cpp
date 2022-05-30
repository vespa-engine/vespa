// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_metric_snapshot.h"

namespace vespalib {

SimpleMetricSnapshot::SimpleMetricSnapshot(uint32_t prevTime, uint32_t currTime)
    : _data(),
      _metrics(_data.setObject()),
      _values(_metrics.setArray("values")),
      _snapLen(currTime - prevTime)
{
    vespalib::slime::Cursor& snapshot = _metrics.setObject("snapshot");
    snapshot.setLong("from", prevTime);
    snapshot.setLong("to",   currTime);
    if (_snapLen < 1.0) {
        _snapLen = 1.0;
    }
}


void
SimpleMetricSnapshot::addCount(const char *name, const char *desc, uint32_t count)
{
    using namespace vespalib::slime::convenience;
    Cursor& value = _values.addObject();
    value.setString("name", name);
    value.setString("description", desc);
    Cursor& inner = value.setObject("values");
    inner.setLong("count", count);
    inner.setDouble("rate", count / _snapLen);
}

void
SimpleMetricSnapshot::addGauge(const char *name, const char *desc, long gauge)
{
    using namespace vespalib::slime::convenience;
    Cursor& value = _values.addObject();
    value.setString("name", name);
    value.setString("description", desc);
    Cursor& inner = value.setObject("values");
    inner.setLong("average", gauge);
    inner.setLong("min", gauge);
    inner.setLong("max", gauge);
    inner.setLong("last", gauge);
    inner.setLong("count", 1);
    inner.setDouble("rate", 1.0 / _snapLen);
}

} // namespace vespalib
