// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_metrics.h"

namespace search {
namespace engine {

TransportMetrics::QueryMetrics::QueryMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("query", "", "Query metrics", parent),
      count("count", "logdefault", "Query requests handled", this),
      latency("latency", "logdefault", "Query request latency", this)
{
}

TransportMetrics::DocsumMetrics::DocsumMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("docsum", "", "Docsum metrics", parent),
      count("count", "logdefault", "Docsum requests handled", this),
      docs("docs", "logdefault", "Total docsums returned", this),
      latency("latency", "logdefault", "Docsum request latency", this)
{
}

TransportMetrics::TransportMetrics()
    : metrics::MetricSet("transport", "", "Transport server metrics", 0),
      updateLock(),
      query(this),
      docsum(this)
{
}

} // namespace engine
} // namespace search
