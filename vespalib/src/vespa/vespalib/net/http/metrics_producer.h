// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace vespalib {

struct MetricsProducer {
    enum class ExpositionFormat {
        JSON,
        Prometheus
    };

    virtual std::string getMetrics(const std::string &consumer, ExpositionFormat format) = 0;
    virtual std::string getTotalMetrics(const std::string &consumer, ExpositionFormat format) = 0;
    virtual ~MetricsProducer() = default;
};

} // namespace vespalib
