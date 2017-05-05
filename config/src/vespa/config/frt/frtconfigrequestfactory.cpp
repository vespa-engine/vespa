// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "frtconfigrequestfactory.h"
#include "frtconfigrequest.h"
#include "frtconfigrequestv2.h"
#include "frtconfigrequestv3.h"
#include <vespa/config/common/trace.h>
#include <vespa/config/common/compressiontype.h>
#include <vespa/vespalib/util/host_name.h>
#include <unistd.h>
#include <limits.h>


namespace config {

/**
 * Factory for creating config requests depending on protocol version;
 */
FRTConfigRequestFactory::FRTConfigRequestFactory(int protocolVersion, int traceLevel, const VespaVersion & vespaVersion, const CompressionType & compressionType)
    : _protocolVersion(protocolVersion),
      _traceLevel(traceLevel),
      _vespaVersion(vespaVersion),
      _hostName(vespalib::HostName::get()),
      _compressionType(compressionType)
{
}

FRTConfigRequestFactory::~FRTConfigRequestFactory() {
}

FRTConfigRequest::UP
FRTConfigRequestFactory::createConfigRequest(const ConfigKey & key, Connection * connection, const ConfigState & state, int64_t serverTimeout) const
{
    if (1 == _protocolVersion) {
        return FRTConfigRequest::UP(new FRTConfigRequestV1(key, connection, state.md5, state.generation, serverTimeout));
    } else if (2 == _protocolVersion) {
        return FRTConfigRequest::UP(new FRTConfigRequestV2(connection, key, state.md5, state.generation, 0u, _hostName, serverTimeout, Trace(_traceLevel)));
    }
    return FRTConfigRequest::UP(new FRTConfigRequestV3(connection, key, state.md5, state.generation, 0u, _hostName, serverTimeout, Trace(_traceLevel), _vespaVersion, _compressionType));
}

} // namespace config
