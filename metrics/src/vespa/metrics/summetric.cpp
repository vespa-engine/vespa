// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summetric.hpp"
#include "valuemetric.h"
#include "countmetric.h"

namespace metrics {

template class SumMetric<ValueMetric<int64_t, int64_t, false>>;
template class SumMetric<ValueMetric<int64_t, int64_t, true>>;
template class SumMetric<ValueMetric<double, double, false>>;
template class SumMetric<ValueMetric<double, double, true>>;
template class SumMetric<CountMetric<uint64_t, true>>;
template class SumMetric<MetricSet>;

} // metrics

