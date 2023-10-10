// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "metricvalueset.hpp"
#include "valuemetricvalues.h"
#include "countmetricvalues.h"

namespace metrics {

std::string
MetricValueClass::toString(const std::string& id) {
    std::ostringstream ost;
    output(id, ost);
    return ost.str();
}

template class MetricValueSet<CountMetricValues<uint64_t>>;
template class MetricValueSet<ValueMetricValues<int64_t, int64_t>>;
template class MetricValueSet<ValueMetricValues<double, double>>;

} // metrics

