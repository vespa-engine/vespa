// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/distance_metric.h>
#include <vespa/vespalib/stllike/string.h>

namespace search::attribute {

class DistanceMetricUtils {
public:
    static vespalib::string to_string(DistanceMetric metric);
    static DistanceMetric to_distance_metric(const vespalib::string& metric);
};

}
