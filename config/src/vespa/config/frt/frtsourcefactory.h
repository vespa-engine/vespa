// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "frtconfigrequestfactory.h"
#include <vespa/config/common/sourcefactory.h>
#include <vespa/config/common/timingvalues.h>

namespace config {

class ConnectionFactory;

/**
 * Class for sending and receiving config requests via FRT.
 */
class FRTSourceFactory : public SourceFactory
{
public:
    FRTSourceFactory(const FRTSourceFactory &) = delete;
    FRTSourceFactory & operator =(const FRTSourceFactory &) = delete;
    FRTSourceFactory(std::unique_ptr<ConnectionFactory> connectionFactory, const TimingValues & timingValues,
                     int traceLevel, const VespaVersion & vespaVersion, const CompressionType & compressionType);
    ~FRTSourceFactory() override;
    /**
     * Create source handling config described by key.
     */
    std::unique_ptr<Source> createSource(std::shared_ptr<IConfigHolder> holder, const ConfigKey & key) const override;

private:
    std::shared_ptr<ConnectionFactory> _connectionFactory;
    FRTConfigRequestFactory _requestFactory;
    const TimingValues _timingValues;
};

} // namespace config

