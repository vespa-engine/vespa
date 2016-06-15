// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "lid_reuse_delayer_config.h"
#include <vespa/searchcore/proton/server/documentdbconfig.h>

namespace proton
{

namespace documentmetastore
{


LidReuseDelayerConfig::LidReuseDelayerConfig()
    : _visibilityDelay(0),
      _hasIndexedFields(false)
{
}

LidReuseDelayerConfig::LidReuseDelayerConfig(const DocumentDBConfig &
                                             configSnapshot)
    : _visibilityDelay(configSnapshot.getMaintenanceConfigSP()->
                       getVisibilityDelay()),
      _hasIndexedFields(configSnapshot.getSchemaSP()->getNumIndexFields() > 0)
{
}


} // namespace proton::documentmetastore

} // namespace proton
