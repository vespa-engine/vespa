// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/simple_health_producer.h>
#include <vespa/vespalib/net/simple_component_config_producer.h>
#include <vespa/vespalib/metrics/simple_metrics.h>

namespace config {
namespace sentinel {

struct StateApi {
    vespalib::SimpleHealthProducer myHealth;
    vespalib::SimpleComponentConfigProducer myComponents;
};

} // namespace config::sentinel
} // namespace config
