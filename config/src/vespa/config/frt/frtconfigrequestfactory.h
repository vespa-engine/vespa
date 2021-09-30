// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/configkey.h>
#include <vespa/config/common/configstate.h>
#include <vespa/config/common/trace.h>
#include <vespa/config/common/compressiontype.h>
#include <vespa/config/common/vespa_version.h>
#include "frtconfigrequest.h"
#include "protocol.h"
#include "connection.h"

namespace config {

/**
 * Factory for creating config requests depending on protocol version;
 */
class FRTConfigRequestFactory
{
public:
    FRTConfigRequestFactory(int traceLevel, const VespaVersion & vespaVersion, const CompressionType & compressionType);
    ~FRTConfigRequestFactory();

    FRTConfigRequest::UP createConfigRequest(const ConfigKey & key, Connection * connection, const ConfigState & state, int64_t serverTimeout) const;
private:
    const int _traceLevel;
    const VespaVersion _vespaVersion;
    vespalib::string _hostName;
    const CompressionType _compressionType;
};

} // namespace config

