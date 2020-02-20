// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagemetricsset.h"
#include <vespa/document/fieldvalue/serializablearray.h>

namespace storage {

MessageMemoryUseMetricSet::MessageMemoryUseMetricSet(metrics::MetricSet* owner)
    : metrics::MetricSet("message_memory_use", {{"memory"}}, "Message use from storage messages", owner),
      total("total", {{"memory"}}, "Message use from storage messages", this),
      lowpri("lowpri", {{"memory"}}, "Message use from low priority storage messages", this),
      normalpri("normalpri", {{"memory"}}, "Message use from normal priority storage messages", this),
      highpri("highpri", {{"memory"}}, "Message use from high priority storage messages", this),
      veryhighpri("veryhighpri", {{"memory"}}, "Message use from very high priority storage messages", this)
{}

MessageMemoryUseMetricSet::~MessageMemoryUseMetricSet() = default;

StorageMetricSet::StorageMetricSet()
    : metrics::MetricSet("server", {{"memory"}},
          "Metrics for VDS applications"),
      memoryUse("memoryusage", {{"memory"}}, "", this),
      memoryUse_messages(this),
      memoryUse_visiting("memoryusage_visiting", {{"memory"}},
            "Message use from visiting", this),
      tls_metrics(this),
      fnet_metrics(this)
{}

StorageMetricSet::~StorageMetricSet() = default;

void StorageMetricSet::updateMetrics() {

    // Delta snapshotting is destructive, so if an explicit snapshot is triggered
    // (instead of just regular periodic snapshots), some events will effectively
    // be erased from history. This will no longer be a problem once we move to a
    // metrics system built around absolute (rather than derived) values.
    tls_metrics.update_metrics_with_snapshot_delta();
    fnet_metrics.update_metrics();
}

} // storage
