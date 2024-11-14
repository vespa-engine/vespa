// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "content_proton_metrics.h"

namespace proton {

ContentProtonMetrics::ProtonExecutorMetrics::ProtonExecutorMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("executor", {}, "Metrics for top-level executors shared among all document databases", parent),
      proton("proton", this),
      flush("flush", this),
      match("match", this),
      docsum("docsum", this),
      shared("shared", this),
      warmup("warmup", this),
      field_writer("field_writer", this)
{
}

ContentProtonMetrics::SessionCacheMetrics::SessionCacheMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("session_cache", {}, "Metrics for session caches (search / grouping requests)", parent),
      search("search", this),
      grouping("grouping", this)
{
}

ContentProtonMetrics::SessionCacheMetrics::~SessionCacheMetrics() = default;

ContentProtonMetrics::ProtonExecutorMetrics::~ProtonExecutorMetrics() = default;

ContentProtonMetrics::IndexMetrics::CacheMetrics::CacheMetrics(metrics::MetricSet* parent)
    : metrics::MetricSet("cache", {}, "Metrics for caches", parent),
      postinglist(this, "postinglist", "Posting list cache metrics", "postinglist_cache")
{
}

ContentProtonMetrics::IndexMetrics::CacheMetrics::~CacheMetrics() = default;

ContentProtonMetrics::IndexMetrics::IndexMetrics(metrics::MetricSet* parent)
    : metrics::MetricSet("index", {}, "Metrics for indexes", parent),
      cache(this)
{
}

ContentProtonMetrics::IndexMetrics::~IndexMetrics() = default;

ContentProtonMetrics::ContentProtonMetrics()
    : metrics::MetricSet("content.proton", {}, "Search engine metrics", nullptr),
      configGeneration("config.generation", {}, "The oldest config generation used by this process", this),
      transactionLog(this),
      resourceUsage(this),
      executor(this),
      sessionCache(this),
      index(this)
{
}

ContentProtonMetrics::~ContentProtonMetrics() = default;

}
