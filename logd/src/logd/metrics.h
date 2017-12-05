// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/metrics/metrics_manager.h>

namespace logdemon {

using vespalib::metrics::Dimension;
using vespalib::metrics::Counter;
using vespalib::metrics::MetricsManager;
using vespalib::metrics::Point;

struct Metrics {
    std::shared_ptr<MetricsManager> metrics;
    const Dimension loglevel;
    const Dimension servicename;
    const Counter loglines;

    Metrics(std::shared_ptr<MetricsManager> m)
        : metrics(m),
          loglevel(metrics->dimension("loglevel")),
          servicename(metrics->dimension("service")),
          loglines(metrics->counter("logd.processed.lines"))
    {}

    ~Metrics() {}

    void countLine(const vespalib::string &level,
                   const vespalib::string &service) const
    {
        Point p = metrics->pointBuilder()
                  .bind(loglevel, level)
                  .bind(servicename, service);
        loglines.add(1, p);
    }
};

} // namespace logdemon
