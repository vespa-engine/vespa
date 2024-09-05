// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/net/http/metrics_producer.h>
#include <memory>

namespace vespalib::metrics {

class MetricsManager;

/**
 * Utility class for wiring a MetricsManager into a StateApi.
 **/
class Producer : public vespalib::MetricsProducer {
private:
    std::shared_ptr<MetricsManager> _manager;
public:
    explicit Producer(std::shared_ptr<MetricsManager> m);
    ~Producer() override;
    std::string getMetrics(const std::string &consumer, ExpositionFormat format) override;
    std::string getTotalMetrics(const std::string &consumer, ExpositionFormat format) override;
};

}
