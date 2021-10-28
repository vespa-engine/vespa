// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fnet_metrics_wrapper.h"

namespace storage {

FnetMetricsWrapper::FnetMetricsWrapper(metrics::MetricSet* owner)
    : metrics::MetricSet("fnet", {}, "transport layer metrics", owner),
      _num_connections("num-connections", {}, "total number of connection objects", this)
{
}

FnetMetricsWrapper::~FnetMetricsWrapper() = default;

void
FnetMetricsWrapper::update_metrics()
{
    _num_connections.set(FNET_Connection::get_num_connections());
}

}
