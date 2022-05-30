// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "metrics_producer.h"
#include <mutex>

namespace vespalib {

class SimpleMetricsProducer : public MetricsProducer
{
private:
    std::mutex _lock;
    vespalib::string _metrics;
    vespalib::string _totalMetrics;

public:
    SimpleMetricsProducer();
    ~SimpleMetricsProducer() override;
    void setMetrics(const vespalib::string &metrics);
    vespalib::string getMetrics(const vespalib::string &consumer) override;
    void setTotalMetrics(const vespalib::string &metrics);
    vespalib::string getTotalMetrics(const vespalib::string &consumer) override;
};

} // namespace vespalib

