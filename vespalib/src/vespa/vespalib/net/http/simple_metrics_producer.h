// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "metrics_producer.h"
#include <map>
#include <mutex>

namespace vespalib {

class SimpleMetricsProducer : public MetricsProducer
{
private:
    std::mutex _lock;
    std::map<ExpositionFormat, std::string> _metrics;
    std::map<ExpositionFormat, std::string> _total_metrics;

public:
    SimpleMetricsProducer();
    ~SimpleMetricsProducer() override;
    void setMetrics(const std::string &metrics, ExpositionFormat format);
    std::string getMetrics(const std::string &consumer, ExpositionFormat format) override;
    void setTotalMetrics(const std::string &metrics, ExpositionFormat format);
    std::string getTotalMetrics(const std::string &consumer, ExpositionFormat format) override;
};

} // namespace vespalib

