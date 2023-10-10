// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "trans_log_server_metrics.h"

using search::transactionlog::DomainInfo;
using search::transactionlog::DomainStats;

namespace proton {

TransLogServerMetrics::DomainMetrics::DomainMetrics(metrics::MetricSet *parent,
                                                    const vespalib::string &documentType)
    : metrics::MetricSet("transactionlog", {{"documenttype", documentType}},
            "Transaction log metrics for a document type", parent),
      entries("entries", {}, "The current number of entries in the transaction log", this),
      diskUsage("disk_usage", {}, "The disk usage (in bytes) of the transaction log", this),
      replayTime("replay_time", {}, "The replay time (in seconds) of the transaction log during start-up", this)
{
}

TransLogServerMetrics::DomainMetrics::~DomainMetrics() = default;

void
TransLogServerMetrics::DomainMetrics::update(const DomainInfo &stats)
{
    entries.set(stats.numEntries);
    diskUsage.set(stats.byteSize);
    replayTime.set(stats.maxSessionRunTime.count());
}

void
TransLogServerMetrics::considerAddDomains(const DomainStats &stats)
{
    for (const auto &elem : stats) {
        const vespalib::string &documentType = elem.first;
        if (_domainMetrics.find(documentType) == _domainMetrics.end()) {
            _domainMetrics[documentType] = std::make_unique<DomainMetrics>(_parent, documentType);
        }
    }
}

void
TransLogServerMetrics::considerRemoveDomains(const DomainStats &stats)
{
    for (auto itr = _domainMetrics.begin(); itr != _domainMetrics.end(); ) {
        const vespalib::string &documentType = itr->first;
        if (stats.find(documentType) == stats.end()) {
            _parent->unregisterMetric(*itr->second);
            itr = _domainMetrics.erase(itr);
        } else {
            ++itr;
        }
    }
}

void
TransLogServerMetrics::updateDomainMetrics(const DomainStats &stats)
{
    for (const auto &elem : stats) {
        const vespalib::string &documentType = elem.first;
        _domainMetrics.find(documentType)->second->update(elem.second);
    }
}

TransLogServerMetrics::TransLogServerMetrics(metrics::MetricSet *parent)
    : _parent(parent)
{
}

TransLogServerMetrics::~TransLogServerMetrics() = default;

void
TransLogServerMetrics::update(const DomainStats &stats)
{
    considerAddDomains(stats);
    considerRemoveDomains(stats);
    updateDomainMetrics(stats);
}

} // namespace proton
