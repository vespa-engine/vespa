// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/distance_metric.h>
#include <string>

namespace search::attribute {

class DistanceMetricUtils {
public:
    static std::string to_string(DistanceMetric metric);
    static DistanceMetric to_distance_metric(const std::string& metric);
};

}
