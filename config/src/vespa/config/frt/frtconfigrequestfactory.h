// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/compressiontype.h>
#include <vespa/config/common/vespa_version.h>
#include <vespa/vespalib/util/time.h>
#include <memory>

namespace config {

class FRTConfigRequest;
class ConfigKey;
class Connection;
struct ConfigState;

/**
 * Factory for creating config requests depending on protocol version;
 */
class FRTConfigRequestFactory
{
public:
    FRTConfigRequestFactory(int traceLevel, const VespaVersion & vespaVersion, const CompressionType & compressionType);
    ~FRTConfigRequestFactory();

    std::unique_ptr<FRTConfigRequest>
    createConfigRequest(const ConfigKey & key, Connection * connection,
                        const ConfigState & state, vespalib::duration serverTimeout) const;
private:
    const int             _traceLevel;
    const VespaVersion    _vespaVersion;
    vespalib::string      _hostName;
    const CompressionType _compressionType;
};

} // namespace config

