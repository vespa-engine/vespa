// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bootstrap_config_set_builder.h"

namespace proton::test {

BootstrapConfigSetBuilder::BootstrapConfigSetBuilder()
    : bucketspaces(), documenttypes(), filedistributorrpc(), proton(), telemetry() {
}

BootstrapConfigSetBuilder::~BootstrapConfigSetBuilder() = default;

void BootstrapConfigSetBuilder::add_builders(config::ConfigSet& set, const std::string& config_id) {
    set.addBuilder(config_id, &bucketspaces);
    set.addBuilder(config_id, &documenttypes);
    set.addBuilder(config_id, &filedistributorrpc);
    set.addBuilder(config_id, &proton);
    set.addBuilder(config_id, &telemetry);
}

} // namespace proton::test
