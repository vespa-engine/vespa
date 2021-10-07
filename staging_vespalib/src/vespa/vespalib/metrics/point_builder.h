// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <vespa/vespalib/stllike/string.h>

#include "point.h"
#include "point_map.h"

namespace vespalib {
namespace metrics {

class MetricsManager;

/**
 * Build a Point for labeling metrics
 **/
class PointBuilder {
private:
    std::shared_ptr<MetricsManager> _owner;
    PointMap _map;

public:
    // for use from MetricsManager
    PointBuilder(std::shared_ptr<MetricsManager> m);
    PointBuilder(std::shared_ptr<MetricsManager> m, const PointMap &from);
    ~PointBuilder() {}

    /**
     * Bind a dimension to a label.
     * Overwrites any label already bound to that dimension.
     **/
    PointBuilder &&bind(Dimension dimension, Label label) &&;
    PointBuilder &bind(Dimension dimension, Label label) &;

    /**
     * Bind a dimension to a label.
     * Convenience method that converts the label value.
     **/
    PointBuilder &&bind(Dimension dimension, LabelValue label) &&;
    PointBuilder &bind(Dimension dimension, LabelValue label) &;

    /**
     * Bind a dimension to a label.
     * Convenience method that converts both the dimension name and the label value.
     **/
    PointBuilder &&bind(DimensionName dimension, LabelValue label) &&;
    PointBuilder &bind(DimensionName dimension, LabelValue label) &;

    /** make a Point from the builder */
    Point build();

    /** make a Point from the builder */
    operator Point () &&;
};

} // namespace vespalib::metrics
} // namespace vespalib
