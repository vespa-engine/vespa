// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_metrics.h"

namespace search::engine {

TransportMetrics::QueryMetrics::QueryMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("query", "", "Query metrics", parent),
      count("count", "logdefault", "Query requests handled", this),
      latency("latency", "logdefault", "Query request latency", this)
{
}

TransportMetrics::QueryMetrics::~QueryMetrics() = default;

TransportMetrics::DocsumMetrics::DocsumMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("docsum", "", "Docsum metrics", parent),
      count("count", "logdefault", "Docsum requests handled", this),
      docs("docs", "logdefault", "Total docsums returned", this),
      latency("latency", "logdefault", "Docsum request latency", this)
{
}

TransportMetrics::DocsumMetrics::~DocsumMetrics() = default;

TransportMetrics::TransportMetrics()
    : metrics::MetricSet("transport", "", "Transport server metrics", nullptr),
      updateLock(),
      query(this),
      docsum(this)
{
}

TransportMetrics::~TransportMetrics() = default;

}

