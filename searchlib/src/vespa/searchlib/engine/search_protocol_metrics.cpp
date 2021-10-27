// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "search_protocol_metrics.h"

namespace search::engine {

SearchProtocolMetrics::QueryMetrics::QueryMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("query", {}, "Query metrics", parent),
      latency("latency", {{"logdefault"}}, "Query request latency (seconds)", this),
      request_size("request_size", {{"logdefault"}}, "Query request size (network bytes)", this),
      reply_size("reply_size", {{"logdefault"}}, "Query reply size (network bytes)", this)
{
}
SearchProtocolMetrics::QueryMetrics::~QueryMetrics() = default;

SearchProtocolMetrics::DocsumMetrics::DocsumMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("docsum", {}, "Docsum metrics", parent),
      latency("latency", {{"logdefault"}}, "Docsum request latency (seconds)", this),
      request_size("request_size", {{"logdefault"}}, "Docsum request size (network bytes)", this),
      reply_size("reply_size", {{"logdefault"}}, "Docsum reply size (network bytes)", this),
      requested_documents("requested_documents", {{"logdefault"}}, "Total requested document summaries", this)
{
}
SearchProtocolMetrics::DocsumMetrics::~DocsumMetrics() = default;

SearchProtocolMetrics::SearchProtocolMetrics()
    : metrics::MetricSet("search_protocol", {}, "Search protocol server metrics", nullptr),
      _lock(),
      _query(this),
      _docsum(this)
{
}

SearchProtocolMetrics::~SearchProtocolMetrics() = default;

void
SearchProtocolMetrics::update_query_metrics(const QueryStats &stats)
{
    auto guard = std::lock_guard(_lock);
    _query.latency.set(stats.latency);
    _query.request_size.set(stats.request_size);
    _query.reply_size.set(stats.reply_size);
}

void
SearchProtocolMetrics::update_docsum_metrics(const DocsumStats &stats)
{
    auto guard = std::lock_guard(_lock);
    _docsum.latency.set(stats.latency);
    _docsum.request_size.set(stats.request_size);
    _docsum.reply_size.set(stats.reply_size);
    _docsum.requested_documents.inc(stats.requested_documents);
}

}
