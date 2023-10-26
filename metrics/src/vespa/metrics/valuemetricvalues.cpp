// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "valuemetricvalues.hpp"

namespace metrics {

template struct ValueMetricValues<int64_t, int64_t>;
template std::ostream & operator << (std::ostream & os, const ValueMetricValues<int64_t, int64_t> & v);

template struct ValueMetricValues<double, double>;
template std::ostream & operator << (std::ostream & os, const ValueMetricValues<double, double> & v);

} // metrics

