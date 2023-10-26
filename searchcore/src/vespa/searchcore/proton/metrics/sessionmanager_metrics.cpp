// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sessionmanager_metrics.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace proton {

SessionManagerMetrics::SessionManagerMetrics(const vespalib::string &name, metrics::MetricSet *parent)
    : metrics::MetricSet(name, {}, vespalib::make_string("Session manager cache metrics for %s", name.c_str()), parent),
      numInsert("num_insert", {}, "Number of inserted sessions", this),
      numPick("num_pick", {}, "Number if picked sessions", this),
      numDropped("num_dropped", {}, "Number of dropped cached sessions", this),
      numCached("num_cached", {}, "Number of currently cached sessions", this),
      numTimedout("num_timedout", {}, "Number of timed out sessions", this)
{
}

SessionManagerMetrics::~SessionManagerMetrics() = default;

void
SessionManagerMetrics::update(const proton::matching::SessionManager::Stats &stats)
{
    numInsert.inc(stats.numInsert);
    numPick.inc(stats.numPick);
    numDropped.inc(stats.numDropped);
    numCached.set(stats.numCached);
    numTimedout.inc(stats.numTimedout);
}

}
