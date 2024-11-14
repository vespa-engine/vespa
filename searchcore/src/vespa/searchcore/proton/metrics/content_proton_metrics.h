// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "cache_metrics.h"
#include "executor_metrics.h"
#include "resource_usage_metrics.h"
#include "trans_log_server_metrics.h"
#include "sessionmanager_metrics.h"

namespace proton {

/**
 * Metric set for all metrics reported by proton.
 *
 * This class uses the new metric naming scheme decided in architect meeting 2014-10-30.
 * All proton metrics use the prefix "content.proton." and dimensions where appropriate.
 * For instance, all document db metrics use the dimension "documenttype":"mydoctype"
 * instead of using the document type name as part of metric names.
 */
struct ContentProtonMetrics : metrics::MetricSet
{
    struct ProtonExecutorMetrics : metrics::MetricSet {

        ExecutorMetrics proton;
        ExecutorMetrics flush;
        ExecutorMetrics match;
        ExecutorMetrics docsum;
        ExecutorMetrics shared;
        ExecutorMetrics warmup; //TODO not used anymore, remove
        ExecutorMetrics field_writer;

        explicit ProtonExecutorMetrics(metrics::MetricSet *parent);
        ~ProtonExecutorMetrics() override;
    };

    struct SessionCacheMetrics : metrics::MetricSet {
        SessionManagerMetrics search;
        SessionManagerMetrics grouping;

        explicit SessionCacheMetrics(metrics::MetricSet *parent);
        ~SessionCacheMetrics() override;
    };

    struct IndexMetrics : public metrics::MetricSet {
        struct CacheMetrics : public metrics::MetricSet {

            proton::CacheMetrics postinglist;

            explicit CacheMetrics(metrics::MetricSet* parent);
            ~CacheMetrics() override;
        };

        CacheMetrics     cache;

        explicit IndexMetrics(metrics::MetricSet* parent);
        ~IndexMetrics() override;
    };

    metrics::LongValueMetric configGeneration;
    TransLogServerMetrics transactionLog;
    ResourceUsageMetrics resourceUsage;
    ProtonExecutorMetrics executor;
    SessionCacheMetrics sessionCache;
    IndexMetrics        index;

    ContentProtonMetrics();
    ~ContentProtonMetrics() override;
};

}
