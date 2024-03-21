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
    std::map<ExpositionFormat, vespalib::string> _metrics;
    std::map<ExpositionFormat, vespalib::string> _total_metrics;

public:
    SimpleMetricsProducer();
    ~SimpleMetricsProducer() override;
    void setMetrics(const vespalib::string &metrics, ExpositionFormat format);
    vespalib::string getMetrics(const vespalib::string &consumer, ExpositionFormat format) override;
    void setTotalMetrics(const vespalib::string &metrics, ExpositionFormat format);
    vespalib::string getTotalMetrics(const vespalib::string &consumer, ExpositionFormat format) override;
};

} // namespace vespalib

