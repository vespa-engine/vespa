// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/storageserver/app/dummyservicelayerprocess.h>

#include <vespa/log/log.h>

LOG_SETUP(".process.servicelayer");

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
