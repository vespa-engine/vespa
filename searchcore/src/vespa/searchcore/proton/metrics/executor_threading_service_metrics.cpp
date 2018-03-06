// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_threading_service_metrics.h"
#include "executor_threading_service_stats.h"

namespace proton {

ExecutorThreadingServiceMetrics::ExecutorThreadingServiceMetrics(const std::string &name, metrics::MetricSet *parent)
    : metrics::MetricSet(name, "", "Instance specific threading service metrics", parent),
      master("master", this),
      index("index", this),
      summary("summary", this),
      indexFieldInverter("index_field_inverter", this),
      indexFieldWriter("index_field_writer", this),
      attributeFieldWriter("attribute_field_writer", this)
{
}

ExecutorThreadingServiceMetrics::~ExecutorThreadingServiceMetrics() = default;

void
ExecutorThreadingServiceMetrics::update(const ExecutorThreadingServiceStats &stats)
{
    master.update(stats.getMasterExecutorStats());
    index.update(stats.getIndexExecutorStats());
    summary.update(stats.getSummaryExecutorStats());
    indexFieldInverter.update(stats.getIndexFieldInverterExecutorStats());
    indexFieldWriter.update(stats.getIndexFieldWriterExecutorStats());
    attributeFieldWriter.update(stats.getAttributeFieldWriterExecutorStats());
}

}
