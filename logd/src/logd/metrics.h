// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/metrics/metrics_manager.h>

namespace logdemon {

using vespalib::metrics::Dimension;
using vespalib::metrics::Counter;
using vespalib::metrics::MetricsManager;
using vespalib::metrics::Point;

struct Metrics {
    MetricsManager &metrics;
    const Dimension loglevel;
    const Dimension servicename;
    const Counter loglines;

    Metrics(MetricsManager &m)
        : metrics(m),
          loglevel(m.dimension("loglevel")),
          servicename(m.dimension("servicename")),
          loglines(m.counter("loglines"))
    {}

    void countLine(const vespalib::string &level,
                   const vespalib::string &service) const
    {
        Point p = metrics.pointBuilder()
                  .bind(loglevel, level)
                  .bind(servicename, service);
        loglines.add(1, p);
    }
};

} // namespace logdemon
