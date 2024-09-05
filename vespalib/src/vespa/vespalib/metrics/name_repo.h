// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "dimension.h"
#include "label.h"
#include "metric_id.h"
#include "point.h"

#include "name_collection.h"
#include "point_map_collection.h"
#include <string>

namespace vespalib::metrics {

/**
 * Simple repo class
 **/
class NameRepo
{
private:
    NameCollection _metricNames;
    NameCollection _dimensionNames;
    NameCollection _labelValues;
    PointMapCollection _pointMaps;

    NameRepo() = default;
    ~NameRepo() = default;
public:

    MetricId metric(const std::string &name);
    Dimension dimension(const std::string &name);
    Label label(const std::string &value);

    const std::string& metricName(MetricId metric) const;
    const std::string& dimensionName(Dimension dim) const;
    const std::string& labelValue(Label l) const;

    const PointMap& pointMap(Point from) const;
    Point pointFrom(PointMap map);

    static NameRepo instance;
};

}
