// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feed_metrics.h"

using vespalib::LockGuard;

namespace proton {

FeedMetrics::FeedMetrics()
    : metrics::MetricSet("feed", "", "Feed metrics", 0),
      count("count", "logdefault", "Feed messages handled", this),
      latency("latency", "logdefault", "Feed message latency", this)
{
}

FeedMetrics::~FeedMetrics() {}

PerDocTypeFeedMetrics::PerDocTypeFeedMetrics(MetricSet *parent)
    : MetricSet("feedmetrics", "", "Feed metrics", parent),
      _update_lock(),
      _puts("puts", "", "Number of feed put operations", this),
      _updates("updates", "", "Number of feed update operations", this),
      _removes("removes", "", "Number of feed remove operations", this),
      _moves("moves", "", "Number of feed move operations", this),
      _put_latency("put_latency", "", "Latency for feed puts", this),
      _update_latency("update_latency", "", "Latency for feed updates", this),
      _remove_latency("remove_latency", "", "Latency for feed removes", this),
      _move_latency("move_latency", "", "Latency for feed moves", this)
{
}

PerDocTypeFeedMetrics::~PerDocTypeFeedMetrics() {}

void PerDocTypeFeedMetrics::RegisterPut(const FastOS_Time &start_time) {
    LockGuard lock(_update_lock);
    _puts.inc(1);
    _put_latency.addValue(start_time.MilliSecsToNow() / 1000.0);
}

void PerDocTypeFeedMetrics::RegisterUpdate(const FastOS_Time &start_time) {
    LockGuard lock(_update_lock);
    _updates.inc(1);
    _update_latency.addValue(start_time.MilliSecsToNow() / 1000.0);
}

void PerDocTypeFeedMetrics::RegisterRemove(const FastOS_Time &start_time) {
    LockGuard lock(_update_lock);
    _removes.inc(1);
    _remove_latency.addValue(start_time.MilliSecsToNow() / 1000.0);
}

void
PerDocTypeFeedMetrics::RegisterMove(const FastOS_Time &start_time)
{
    LockGuard lock(_update_lock);
    _moves.inc(1);
    _move_latency.addValue(start_time.MilliSecsToNow() / 1000.0);
}

} // namespace proton
