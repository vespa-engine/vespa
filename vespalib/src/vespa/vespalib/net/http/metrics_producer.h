// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

struct MetricsProducer {
    enum class ExpositionFormat {
        JSON,
        Prometheus
    };

    virtual vespalib::string getMetrics(const vespalib::string &consumer, ExpositionFormat format) = 0;
    virtual vespalib::string getTotalMetrics(const vespalib::string &consumer, ExpositionFormat format) = 0;
    virtual ~MetricsProducer() = default;
};

} // namespace vespalib
