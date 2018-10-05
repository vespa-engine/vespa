// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "dimension.h"
#include "label.h"
#include "metric_name.h"
#include "point.h"

#include "name_collection.h"
#include "point_map_collection.h"

namespace vespalib {
namespace metrics {

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

    MetricName metric(const vespalib::string &name);
    Dimension dimension(const vespalib::string &name);
    Label label(const vespalib::string &value);

    const vespalib::string& metricName(MetricName metric);
    const vespalib::string& dimensionName(Dimension dim);
    const vespalib::string& labelValue(Label l);

    const PointMap::BackingMap& pointMap(Point from);
    Point pointFrom(PointMap::BackingMap map);

    static NameRepo instance;
};

} // namespace vespalib::metrics
} // namespace vespalib

