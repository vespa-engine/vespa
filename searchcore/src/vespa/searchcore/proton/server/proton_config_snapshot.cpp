// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proton_config_snapshot.h"

namespace proton {

ProtonConfigSnapshot::ProtonConfigSnapshot(std::shared_ptr<BootstrapConfig> bootstrapConfig,
                                           DocumentDBConfigs documentDBConfigs)
    : _bootstrapConfig(std::move(bootstrapConfig)),
      _documentDBConfigs(std::move(documentDBConfigs))
{
}

ProtonConfigSnapshot::~ProtonConfigSnapshot()
{
}

} // namespace proton
