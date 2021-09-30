// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitormetrics.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage {

VisitorMetrics::VisitorMetrics()
    : metrics::MetricSet("visitor", {{"visitor"}}, ""),
      queueSize("cv_queuesize", {}, "Size of create visitor queue", this),
      queueSkips("cv_skipqueue", {},
              "Number of times we could skip queue as we had free visitor "
              "spots", this),
      queueFull("cv_queuefull", {},
              "Number of create visitor messages failed as queue is full",
              this),
      queueWaitTime("cv_queuewaittime", {},
              "Milliseconds waiting in create visitor queue, for visitors "
              "that was added to visitor queue but scheduled later", this),
      queueTimeoutWaitTime("cv_queuetimeoutwaittime", {},
              "Milliseconds waiting in create visitor queue, for visitors "
              "that timed out while in the visitor quueue", this),
      queueEvictedWaitTime("cv_queueevictedwaittime", {},
              "Milliseconds waiting in create visitor queue, for visitors "
              "that was evicted from queue due to higher priority visitors "
              "coming", this),
      threads(),
      sum("allthreads", {{"sum"}}, "", this)
{
    queueSize.unsetOnZeroValue();
}

VisitorMetrics::~VisitorMetrics() = default;

void
VisitorMetrics::initThreads(uint16_t threadCount) {
    if (!threads.empty()) {
        throw vespalib::IllegalStateException("Cannot initialize visitor metrics twice", VESPA_STRLOC);
    }
    threads.clear();
    threads.resize(threadCount);
    for (uint32_t i=0; i<threads.size(); ++i) {
        vespalib::asciistream ost;
        ost << "visitor_thread_" << i;
        threads[i] = std::make_shared<VisitorThreadMetrics>( ost.str(), ost.str());
        registerMetric(*threads[i]);
        sum.addMetricToSum(*threads[i]);
    }
}

}
