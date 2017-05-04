// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "metrics_producer.h"
#include <vespa/vespalib/util/sync.h>

namespace vespalib {

class SimpleMetricsProducer : public MetricsProducer
{
private:
    Lock _lock;
    vespalib::string _metrics;
    vespalib::string _totalMetrics;

public:
    SimpleMetricsProducer();
    ~SimpleMetricsProducer();
    void setMetrics(const vespalib::string &metrics);
    virtual vespalib::string getMetrics(const vespalib::string &consumer) override;
    void setTotalMetrics(const vespalib::string &metrics);
    virtual vespalib::string getTotalMetrics(const vespalib::string &consumer) override;
};

} // namespace vespalib

