// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/metrics/metrics_manager.h>

namespace logdemon {

using vespalib::metrics::Dimension;
using vespalib::metrics::Counter;
using vespalib::metrics::MetricsManager;
using vespalib::metrics::Point;

/**
 * Tracks metrics for number of processed log lines.
 */
struct Metrics {
    std::shared_ptr<MetricsManager> metrics;
    const Dimension loglevel;
    const Dimension servicename;
    const Counter loglines;

    Metrics(std::shared_ptr<MetricsManager> m);
    ~Metrics();

    void countLine(const vespalib::string &level, const vespalib::string &service) const;
};

} // namespace logdemon
