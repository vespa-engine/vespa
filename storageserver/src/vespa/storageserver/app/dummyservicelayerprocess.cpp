// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummyservicelayerprocess.h"

namespace storage {

// DummyServiceLayerProcess implementation

DummyServiceLayerProcess::DummyServiceLayerProcess(const config::ConfigUri & configUri)
    : ServiceLayerProcess(configUri, vespalib::HwInfo())
{
}

void
DummyServiceLayerProcess::shutdown()
{
    ServiceLayerProcess::shutdown();
    _provider.reset();
}

void
DummyServiceLayerProcess::setupProvider()
{
    _provider = std::make_unique<spi::dummy::DummyPersistence>(getTypeRepo());
}

} // storage
