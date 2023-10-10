// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "metrics.h"

namespace logdemon {

Metrics::Metrics(std::shared_ptr<MetricsManager> m)
    : metrics(std::move(m)),
      loglevel(metrics->dimension("loglevel")),
      servicename(metrics->dimension("service")),
      loglines(metrics->counter("logd.processed.lines",
                                "how many log lines have been processed"))
{}

Metrics::~Metrics() = default;

void
Metrics::countLine(const vespalib::string &level, const vespalib::string &service) const
{
    Point p = metrics->pointBuilder()
            .bind(loglevel, level)
            .bind(servicename, service);
    loglines.add(1, p);
}

}
