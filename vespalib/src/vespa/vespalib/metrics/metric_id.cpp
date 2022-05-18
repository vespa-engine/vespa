// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "metric_id.h"
#include "name_repo.h"

namespace vespalib::metrics {

MetricId
MetricId::from_name(const vespalib::string& name)
{
    return NameRepo::instance.metric(name);
}

const vespalib::string&
MetricId::as_name() const
{
    return NameRepo::instance.metricName(*this);
}

} // namespace vespalib::metrics
