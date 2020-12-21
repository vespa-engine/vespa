// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "connectionfactory.h"
#include "frtconfigrequestfactory.h"
#include <vespa/config/common/sourcefactory.h>
#include <vespa/config/common/timingvalues.h>

namespace config {

/**
 * Class for sending and receiving config requests via FRT.
 */
class FRTSourceFactory : public SourceFactory
{
public:
    FRTSourceFactory(ConnectionFactory::UP connectionFactory, const TimingValues & timingValues, int traceLevel, const VespaVersion & vespaVersion, const CompressionType & compressionType);

    /**
     * Create source handling config described by key.
     */
    Source::UP createSource(const IConfigHolder::SP & holder, const ConfigKey & key) const override;

private:
    ConnectionFactory::SP _connectionFactory;
    FRTConfigRequestFactory _requestFactory;
    const TimingValues _timingValues;
};

} // namespace config

