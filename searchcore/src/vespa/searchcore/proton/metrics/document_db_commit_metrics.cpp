// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_commit_metrics.h"

namespace proton {

DocumentDBCommitMetrics::DocumentDBCommitMetrics(metrics::MetricSet* parent)
    : MetricSet("commit", {}, "commit metrics for feeding in a document database", parent),
      operations("operations", {}, "Number of operations included in a commit", this),
      latency("latency", {}, "Latency for commit", this)
{
}

DocumentDBCommitMetrics::~DocumentDBCommitMetrics() = default;

}
