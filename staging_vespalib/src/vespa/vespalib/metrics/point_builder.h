// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <vespa/vespalib/stllike/string.h>

#include "point.h"
#include "point_map.h"

namespace vespalib {
namespace metrics {

class MetricsManager;

class PointBuilder {
private:
    std::shared_ptr<MetricsManager> _owner;
    PointMapBacking _map;

public:
    PointBuilder(std::shared_ptr<MetricsManager> m);
    PointBuilder(std::shared_ptr<MetricsManager> m, const PointMapBacking &from);
    ~PointBuilder() {}

    PointBuilder &&bind(Dimension dimension, Label label) &&;
    PointBuilder &&bind(Dimension dimension, LabelValue label) &&;
    PointBuilder &&bind(DimensionName dimension, LabelValue label) &&;

    Point build();
    operator Point () &&;
};

} // namespace vespalib::metrics
} // namespace vespalib
