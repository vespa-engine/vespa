// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::VisitorMetrics
 * @ingroup visiting
 *
 * @brief Metrics for visiting.
 *
 * @version $Id$
 */
#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/storage/visiting/visitorthreadmetrics.h>

namespace storage {

struct VisitorMetrics : public metrics::MetricSet
{
    metrics::LongAverageMetric queueSize;
    metrics::LongCountMetric queueSkips;
    metrics::LongCountMetric queueFull;
    metrics::LongAverageMetric queueWaitTime;
    metrics::LongAverageMetric queueTimeoutWaitTime;
    metrics::LongAverageMetric queueEvictedWaitTime;
    std::vector<std::shared_ptr<VisitorThreadMetrics> > threads;
    metrics::SumMetric<MetricSet> sum;

    VisitorMetrics()
        : metrics::MetricSet("visitor", "visitor", ""),
          queueSize("cv_queuesize", "", "Size of create visitor queue", this),
          queueSkips("cv_skipqueue", "",
                  "Number of times we could skip queue as we had free visitor "
                  "spots", this),
          queueFull("cv_queuefull", "",
                  "Number of create visitor messages failed as queue is full",
                  this),
          queueWaitTime("cv_queuewaittime", "",
                  "Milliseconds waiting in create visitor queue, for visitors "
                  "that was added to visitor queue but scheduled later", this),
          queueTimeoutWaitTime("cv_queuetimeoutwaittime", "",
                  "Milliseconds waiting in create visitor queue, for visitors "
                  "that timed out while in the visitor quueue", this),
          queueEvictedWaitTime("cv_queueevictedwaittime", "",
                  "Milliseconds waiting in create visitor queue, for visitors "
                  "that was evicted from queue due to higher priority visitors "
                  "coming", this),
          threads(),
          sum("allthreads", "sum", "", this)
    {
        queueSize.unsetOnZeroValue();
    }

    void initThreads(uint16_t threadCount,
                     const metrics::LoadTypeSet& loadTypes)
    {
        if (!threads.empty()) {
            throw vespalib::IllegalStateException(
                    "Cannot initialize visitor metrics twice", VESPA_STRLOC);
        }
        threads.clear();
        threads.resize(threadCount);
        for (uint32_t i=0; i<threads.size(); ++i) {
            std::ostringstream ost;
            ost << "visitor_thread_" << i;
            threads[i].reset(new VisitorThreadMetrics(
                                     ost.str(),
                                     ost.str(),
                                     loadTypes));
            registerMetric(*threads[i]);
            sum.addMetricToSum(*threads[i]);
        }
    }
};

} // storage

