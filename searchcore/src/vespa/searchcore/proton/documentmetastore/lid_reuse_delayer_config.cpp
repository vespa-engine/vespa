// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_reuse_delayer_config.h"
#include <vespa/searchcore/proton/server/documentdbconfig.h>

namespace proton::documentmetastore {

LidReuseDelayerConfig::LidReuseDelayerConfig(const DocumentDBConfig & configSnapshot)
    : _visibilityDelay(configSnapshot.getMaintenanceConfigSP()->getVisibilityDelay()),
      _allowEarlyAck(configSnapshot.getMaintenanceConfigSP()->allowEarlyAck()),
      _hasIndexedOrAttributeFields(configSnapshot.getSchemaSP()->getNumIndexFields() > 0 ||
                                   configSnapshot.getSchemaSP()->getNumAttributeFields() > 0)
{
}

LidReuseDelayerConfig::LidReuseDelayerConfig()
    : LidReuseDelayerConfig(vespalib::duration::zero(), false)
{}

LidReuseDelayerConfig::LidReuseDelayerConfig(vespalib::duration visibilityDelay, bool hasIndexedOrAttributeFields_in)
    : _visibilityDelay(visibilityDelay),
      _allowEarlyAck(visibilityDelay > vespalib::duration::zero()),
      _hasIndexedOrAttributeFields(hasIndexedOrAttributeFields_in)
{
}

}
