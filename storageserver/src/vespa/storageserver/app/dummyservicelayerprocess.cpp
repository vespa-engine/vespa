// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummyservicelayerprocess.h"

namespace storage {

// DummyServiceLayerProcess implementation

DummyServiceLayerProcess::DummyServiceLayerProcess(const config::ConfigUri & configUri)
    : ServiceLayerProcess(configUri)
{
}

void
DummyServiceLayerProcess::shutdown()
{
    ServiceLayerProcess::shutdown();
    _provider.reset(0);
}

void
DummyServiceLayerProcess::setupProvider()
{
    _provider.reset(new spi::dummy::DummyPersistence(getTypeRepo()));
}

} // storage
