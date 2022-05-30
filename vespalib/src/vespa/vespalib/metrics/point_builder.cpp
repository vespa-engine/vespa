// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "point.h"
#include "metrics_manager.h"

namespace vespalib {
namespace metrics {

PointBuilder::PointBuilder(std::shared_ptr<MetricsManager> m)
    : _owner(std::move(m)), _map()
{}

PointBuilder::PointBuilder(std::shared_ptr<MetricsManager> m,
                           const PointMap &copyFrom)
    : _owner(std::move(m)), _map(copyFrom)
{}

PointBuilder &
PointBuilder::bind(Dimension dimension, Label label) &
{
    _map.erase(dimension);
    _map.emplace(dimension, label);
    return *this;
}
PointBuilder &
PointBuilder::bind(Dimension dimension, LabelValue label) &
{
    Label c = _owner->label(label);
    return bind(dimension, c);
}

PointBuilder &
PointBuilder::bind(DimensionName dimension, LabelValue label) &
{
    Dimension a = _owner->dimension(dimension);
    Label c = _owner->label(label);
    return bind(a, c);
}

PointBuilder &&
PointBuilder::bind(Dimension dimension, Label label) &&
{
    bind(dimension, label);
    return std::move(*this);
}

PointBuilder &&
PointBuilder::bind(Dimension dimension, LabelValue label) &&
{
    bind(dimension, label);
    return std::move(*this);
}

PointBuilder &&
PointBuilder::bind(DimensionName dimension, LabelValue label) &&
{
    bind(dimension, label);
    return std::move(*this);
}

Point
PointBuilder::build()
{
    return _owner->pointFrom(_map);
}

PointBuilder::operator Point() &&
{
    return _owner->pointFrom(std::move(_map));
}

} // namespace vespalib::metrics
} // namespace vespalib
