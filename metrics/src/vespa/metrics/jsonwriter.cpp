// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "jsonwriter.h"

#include "countmetric.h"
#include "valuemetric.h"
#include "metricsnapshot.h"

#include <iterator>
#include <cassert>

namespace metrics {

JsonWriter::JsonWriter(vespalib::JsonStream& stream)
    : _stream(stream),
      _flag(NOT_STARTED),
      _dimensionStack(),
      _period(0)
{
}

bool
JsonWriter::visitSnapshot(const MetricSnapshot& snapshot)
{
    _stream << Object()
        << "snapshot" << Object()
            << "from" << vespalib::count_s(snapshot.getFromTime().time_since_epoch())
            << "to" << vespalib::count_s(snapshot.getToTime().time_since_epoch())
        << End()
        << "values" << Array();
    _flag = SNAPSHOT_STARTED;
    _period = vespalib::count_s(snapshot.getPeriod()); // Only prints second resolution
    return true;
}

void
JsonWriter::doneVisitingSnapshot(const MetricSnapshot&)
{
    assert(_flag == SNAPSHOT_STARTED);
    _stream << End() << End();
    _flag = NOT_STARTED;
    _period = 0;
}

bool
JsonWriter::visitMetricSet(const MetricSet& set, bool)
{
    _dimensionStack.push_back(set.getTags());
    return true;
}

void
JsonWriter::doneVisitingMetricSet(const MetricSet&)
{
    _dimensionStack.pop_back();
}

void
JsonWriter::writeCommonPrefix(const Metric& m)
{
    if (_flag == NOT_STARTED) {
        _stream << Array();
        _flag = METRICS_WRITTEN;
    }
    _stream << Object()
        << "name" << m.getPath()
        << "description" << m.getDescription();
}

void
JsonWriter::writeDimensions(const DimensionSet& dimensions)
{
    for (const auto& dimension : dimensions) {
        if (!dimension.key().empty() && !dimension.value().empty()) {
            _stream << dimension.key() << dimension.value();
        }
    }
}

void
JsonWriter::writeInheritedDimensions()
{
    for (const auto& dimensions : _dimensionStack) {
        writeDimensions(dimensions);
    }
}

void
JsonWriter::writeMetricSpecificDimensions(const Metric& m)
{
    if (isLeafMetric(m)) {
        writeDimensions(m.getTags());
    }
}

void
JsonWriter::writeCommonPostfix(const Metric& m)
{
    _stream << "dimensions" << Object();

    writeInheritedDimensions();
    writeMetricSpecificDimensions(m);

    _stream << End() << End();
}

bool
JsonWriter::visitCountMetric(const AbstractCountMetric& m, bool)
{
    writeCommonPrefix(m);
    uint64_t count = m.getLongValue("count");
    _stream << "values" << Object()
        << "count" << count;
    if (_period > 0) {
        uint64_t rate = 1000000 * count / _period;
        _stream << "rate" << (rate / 1000000.0);
    }
    _stream << End();
    writeCommonPostfix(m);
    return true;
}

bool
JsonWriter::visitValueMetric(const AbstractValueMetric& m, bool)
{
    writeCommonPrefix(m);
    MetricValueClass::UP values(m.getValues());
    _stream << "values" << Object()
        << "average";
    if (values->getLongValue("count") == 0) {
        _stream << 0.0;
    } else {
        _stream << (values->getDoubleValue("total")
                    / values->getDoubleValue("count"));
    }
    _stream << "sum" << values->getDoubleValue("total");
    _stream << "count";
    values->output("count", _stream);
    if (_period > 0) {
        uint64_t rate = 1000000 * values->getLongValue("count") / _period;
        _stream << "rate" << (rate / 1000000.0);
    }
    _stream << "min";
    values->output("min", _stream);
    _stream << "max";
    values->output("max", _stream);
    _stream << "last";
    values->output("last", _stream);
    _stream << End();
    writeCommonPostfix(m);
    return true;
}

void
JsonWriter::doneVisiting()
{
    if (_flag == METRICS_WRITTEN) {
        _stream << End();
        _flag = NOT_STARTED;
    }
    assert(_flag == NOT_STARTED);
}

} // metrics
