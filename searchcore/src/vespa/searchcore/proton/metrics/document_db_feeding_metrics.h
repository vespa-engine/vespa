// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_db_commit_metrics.h"

namespace proton {

/*
 * Metrics for feeding within a document db.
 */
struct DocumentDBFeedingMetrics : metrics::MetricSet
{
    DocumentDBCommitMetrics commit;

    DocumentDBFeedingMetrics(metrics::MetricSet* parent);
    ~DocumentDBFeedingMetrics() override;
};

}
