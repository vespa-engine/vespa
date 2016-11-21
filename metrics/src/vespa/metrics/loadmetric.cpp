// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "loadmetric.hpp"
#include "valuemetric.h"
#include "countmetric.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace metrics {

vespalib::string
LoadType::toString() const {
    return vespalib::make_string("%s(%u)", _name.c_str(), _id);
}

template class LoadMetric<ValueMetric<int64_t, int64_t, false>>;
template class LoadMetric<ValueMetric<int64_t, int64_t, true>>;
template class LoadMetric<ValueMetric<double, double, false>>;
template class LoadMetric<ValueMetric<double, double, true>>;
template class LoadMetric<CountMetric<uint64_t, true>>;

}
