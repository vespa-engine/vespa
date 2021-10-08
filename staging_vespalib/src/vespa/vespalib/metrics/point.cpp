// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "point.h"
#include "name_repo.h"

namespace vespalib {
namespace metrics {

Point Point::empty(0);

Point
Point::from_map(const PointMap& map)
{
    return NameRepo::instance.pointFrom(map);
}

Point
Point::from_map(PointMap&& map)
{
    return NameRepo::instance.pointFrom(std::move(map));
}

const PointMap&
Point::as_map() const
{
    return NameRepo::instance.pointMap(*this);
}

} // namespace vespalib::metrics
} // namespace vespalib
