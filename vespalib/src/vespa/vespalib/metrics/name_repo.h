// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "dimension.h"
#include "label.h"
#include "metric_id.h"
#include "point.h"

#include "name_collection.h"
#include "point_map_collection.h"

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

    MetricId metric(const vespalib::string &name);
    Dimension dimension(const vespalib::string &name);
    Label label(const vespalib::string &value);

    const vespalib::string& metricName(MetricId metric) const;
    const vespalib::string& dimensionName(Dimension dim) const;
    const vespalib::string& labelValue(Label l) const;

    const PointMap& pointMap(Point from) const;
    Point pointFrom(PointMap map);

    static NameRepo instance;
};

}
