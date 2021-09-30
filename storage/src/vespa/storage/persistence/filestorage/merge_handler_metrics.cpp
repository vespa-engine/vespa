// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "merge_handler_metrics.h"
#include <vespa/metrics/metricset.h>

namespace storage {

MergeHandlerMetrics::MergeHandlerMetrics(metrics::MetricSet* owner)
    : bytesMerged("bytesmerged", {}, "Total number of bytes merged into this node.", owner),
      mergeLatencyTotal("mergelatencytotal", {},
                        "Latency of total merge operation, from master node receives "
                        "it, until merge is complete and master node replies.", owner),
      mergeMetadataReadLatency("mergemetadatareadlatency", {},
                               "Latency of time used in a merge step to check metadata of "
                               "current node to see what data it has.", owner),
      mergeDataReadLatency("mergedatareadlatency", {},
                           "Latency of time used in a merge step to read data other "
                           "nodes need.", owner),
      mergeDataWriteLatency("mergedatawritelatency", {},
                            "Latency of time used in a merge step to write data needed to "
                            "current node.", owner),
      mergeAverageDataReceivedNeeded("mergeavgdatareceivedneeded", {}, "Amount of data transferred from previous node "
                                                                       "in chain that we needed to apply locally.", owner),
      put_latency("put_latency", {}, "Latency of individual puts that are part of merge operations", owner),
      remove_latency("remove_latency", {}, "Latency of individual removes that are part of merge operations", owner)
{}

MergeHandlerMetrics::~MergeHandlerMetrics() = default;

}
