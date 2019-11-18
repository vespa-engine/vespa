// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_reuse_delayer_config.h"
#include <vespa/searchcore/proton/server/documentdbconfig.h>

namespace proton::documentmetastore {

LidReuseDelayerConfig::LidReuseDelayerConfig()
    : _visibilityDelay(0),
      _hasIndexedOrAttributeFields(false)
{
}

LidReuseDelayerConfig::LidReuseDelayerConfig(const DocumentDBConfig &
                                             configSnapshot)
    : _visibilityDelay(configSnapshot.getMaintenanceConfigSP()->getVisibilityDelay()),
      _hasIndexedOrAttributeFields(configSnapshot.getSchemaSP()->getNumIndexFields() > 0 ||
                                   configSnapshot.getSchemaSP()->getNumAttributeFields() > 0)
{
}


} // namespace proton::documentmetastore

