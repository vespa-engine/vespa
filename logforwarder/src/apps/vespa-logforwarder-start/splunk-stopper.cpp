// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "splunk-stopper.h"
#include "child-handler.h"

#include <vespa/log/log.h>
LOG_SETUP(".splunk-stopper");

SplunkStopper::SplunkStopper(const char *configId) {
    start(configId);
}

SplunkStopper::~SplunkStopper() = default;

void SplunkStopper::gotConfig(const LogforwarderConfig& config) {
    LOG(debug, "got config with splunk home '%s'", config.splunkHome.c_str());
    ChildHandler().stopChild(config.splunkHome);
}
