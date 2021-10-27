// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/countmetric.h>
#include <vespa/metrics/metricset.h>
#include <mutex>

namespace search::engine {

/**
 * Metrics for the proto rpc adapter component implementing the search
 * protocol in proton.
 **/
class SearchProtocolMetrics : public metrics::MetricSet
{
public:
    // sub-metrics for query request/reply
    struct QueryMetrics : metrics::MetricSet {
        metrics::DoubleAverageMetric latency;
        metrics::LongAverageMetric   request_size;
        metrics::LongAverageMetric   reply_size;

        QueryMetrics(metrics::MetricSet *parent);
        ~QueryMetrics() override;
    };

    // value-wrapper used when collecting and reporting query metrics
    struct QueryStats {
        double latency;
        size_t request_size;
        size_t reply_size;
        QueryStats() : latency(0.0), request_size(0), reply_size(0) {}
    };

    // sub-metrics for docsum request/reply
    struct DocsumMetrics : metrics::MetricSet {
        metrics::DoubleAverageMetric latency;
        metrics::LongAverageMetric   request_size;
        metrics::LongAverageMetric   reply_size;
        metrics::LongCountMetric     requested_documents;

        DocsumMetrics(metrics::MetricSet *parent);
        ~DocsumMetrics() override;
    };

    // value-wrapper used when collecting and reporting docsum metrics
    struct DocsumStats {
        double latency;
        size_t request_size;
        size_t reply_size;
        size_t requested_documents;
        DocsumStats() : latency(0.0), request_size(0), reply_size(0), requested_documents(0) {}
    };

private:
    std::mutex    _lock;
    QueryMetrics  _query;
    DocsumMetrics _docsum;

public:
    SearchProtocolMetrics();
    ~SearchProtocolMetrics() override;

    const QueryMetrics &query() const { return _query; }
    const DocsumMetrics &docsum() const { return _docsum; }

    void update_query_metrics(const QueryStats &stats);
    void update_docsum_metrics(const DocsumStats &stats);
};

}
