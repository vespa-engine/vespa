// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/vespalib/util/linkedptr.h>

namespace proton {

struct LegacyAttributeMetrics : metrics::MetricSet {

    // The metric set also owns the actual metrics for individual
    // attribute vectors. Another way to do this would be to let the
    // attribute vectors own their own metrics, but this would
    // generate more dependencies and reduce locality of code changes.

    struct List : metrics::MetricSet {
        struct Entry : metrics::MetricSet {
            typedef vespalib::LinkedPtr<Entry> LP;
            metrics::LongValueMetric memoryUsage;
            metrics::LongValueMetric bitVectors;
            Entry(const std::string &name);
        };
        Entry::LP add(const std::string &name);
        Entry::LP get(const std::string &name) const;
        Entry::LP remove(const std::string &name);
        std::vector<Entry::LP> release();

        // per attribute metrics will be wired in here (by the metrics engine)
        List(metrics::MetricSet *parent);
        ~List();

    private:
        std::map<std::string, Entry::LP> metrics;
    };

    List                     list;
    metrics::LongValueMetric memoryUsage;
    metrics::LongValueMetric bitVectors;

    LegacyAttributeMetrics(metrics::MetricSet *parent);
    ~LegacyAttributeMetrics();
};

} // namespace proton

