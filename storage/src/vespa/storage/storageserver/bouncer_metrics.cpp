// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bouncer_metrics.h"

namespace storage {

BouncerMetrics::BouncerMetrics()
    : MetricSet("bouncer", "", "Metrics for Bouncer component", nullptr),
      clock_skew_aborts("clock_skew_aborts", "", "Number of client operations that were aborted due to "
                        "clock skew between sender and receiver exceeding acceptable range", this)
{
}

BouncerMetrics::~BouncerMetrics() = default;

}