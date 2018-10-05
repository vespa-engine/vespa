// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "legacy_proton_metrics.h"

namespace proton {

LegacyProtonMetrics::DocumentTypeMetrics::DocumentTypeMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("doctypes", {}, "Metrics per document type", parent)
{
}

LegacyProtonMetrics::DocumentTypeMetrics::~DocumentTypeMetrics() { }

LegacyProtonMetrics::LegacyProtonMetrics()
    : metrics::MetricSet("proton", {}, "Search engine metrics", 0),
      docTypes(this),
      executor("executor", this),
      flushExecutor("flushexecutor", this),
      matchExecutor("matchexecutor", this),
      summaryExecutor("summaryexecutor", this),
      memoryUsage("memoryusage", {{"logdefault"}}, "Total tracked memory usage", this),
      diskUsage("diskusage", {{"logdefault"}}, "Total tracked disk usage for disk indexes", this),
      docsInMemory("docsinmemory", {{"logdefault"}}, "Total Number of documents in memory", this),
      numDocs("numdocs", {{"logdefault"}}, "Total number of ready/indexed documents among all document dbs (equal as numindexeddocs)", this),
      numActiveDocs("numactivedocs", {{"logdefault"}},
                    "Total number of active/searchable documents among all document dbs", this),
      numIndexedDocs("numindexeddocs", {{"logdefault"}},
                     "Total number of ready/indexed documents among all document dbs (equal as numdocs)", this),
      numStoredDocs("numstoreddocs", {{"logdefault"}},
                    "Total number of stored documents among all document dbs", this),
      numRemovedDocs("numremoveddocs", {{"logdefault"}},
                    "Total number of removed documents among all document dbs", this)
{
    // supply start value to support sum without any document types
    metrics::LongValueMetric start("start", {}, "", 0);
    memoryUsage.setStartValue(start);
    diskUsage.setStartValue(start);
    docsInMemory.setStartValue(start);
    numDocs.setStartValue(start);
    numActiveDocs.setStartValue(start);
    numIndexedDocs.setStartValue(start);
    numStoredDocs.setStartValue(start);
    numRemovedDocs.setStartValue(start);
}

LegacyProtonMetrics::~LegacyProtonMetrics() {}

} // namespace proton
