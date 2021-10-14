// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "name_repo.h"

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.metrics.name_repo");

namespace vespalib {
namespace metrics {

MetricId
NameRepo::metric(const vespalib::string &name)
{
    size_t id = _metricNames.resolve(name);
    LOG(debug, "metric name %s -> %zu", name.c_str(), id);
    return MetricId(id);
}

Dimension
NameRepo::dimension(const vespalib::string &name)
{
    size_t id = _dimensionNames.resolve(name);
    LOG(debug, "dimension name %s -> %zu", name.c_str(), id);
    return Dimension(id);
}

Label
NameRepo::label(const vespalib::string &value)
{
    size_t id = _labelValues.resolve(value);
    LOG(debug, "label value %s -> %zu", value.c_str(), id);
    return Label(id);
}

const vespalib::string&
NameRepo::metricName(MetricId metric) const
{
    return _metricNames.lookup(metric.id());
}

const vespalib::string&
NameRepo::dimensionName(Dimension dim) const
{
    return _dimensionNames.lookup(dim.id());
}

const vespalib::string&
NameRepo::labelValue(Label l) const
{
    return _labelValues.lookup(l.id());
}


const PointMap&
NameRepo::pointMap(Point from) const
{
    return _pointMaps.lookup(from.id());
}

Point
NameRepo::pointFrom(PointMap map)
{
    size_t id = _pointMaps.resolve(std::move(map));
    return Point(id);
}


NameRepo NameRepo::instance;


} // namespace vespalib::metrics
} // namespace vespalib
