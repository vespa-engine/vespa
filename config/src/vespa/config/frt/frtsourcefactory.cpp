// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "frtsourcefactory.h"
#include "frtsource.h"

namespace config {

FRTSourceFactory::FRTSourceFactory(ConnectionFactory::UP connectionFactory, const TimingValues & timingValues, int protocolVersion, int traceLevel, const VespaVersion & vespaVersion, const CompressionType & compressionType)
    : _connectionFactory(connectionFactory.release()),
      _requestFactory(protocolVersion, traceLevel, vespaVersion, compressionType),
      _timingValues(timingValues)
{
}

Source::UP
FRTSourceFactory::createSource(const IConfigHolder::SP & holder, const ConfigKey & key) const
{
    return Source::UP(new FRTSource(_connectionFactory, _requestFactory, ConfigAgent::UP(new FRTConfigAgent(holder, _timingValues)), key));
}

} // namespace config
