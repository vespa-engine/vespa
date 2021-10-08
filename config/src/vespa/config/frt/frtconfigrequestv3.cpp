// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "frtconfigrequestv3.h"
#include "frtconfigresponsev3.h"
#include "connection.h"
#include <vespa/config/common/trace.h>
#include <vespa/config/common/vespa_version.h>

using namespace config::protocol;

namespace config {

FRTConfigRequestV3::FRTConfigRequestV3(Connection * connection,
                                       const ConfigKey & key,
                                       const vespalib::string & configMd5,
                                       int64_t currentGeneration,
                                       const vespalib::string & hostName,
                                       int64_t serverTimeout,
                                       const Trace & trace,
                                       const VespaVersion & vespaVersion,
                                       const CompressionType & compressionType)
    : SlimeConfigRequest(connection, key, configMd5, currentGeneration, hostName, serverTimeout, trace, vespaVersion, 3, compressionType, "config.v3.getConfig")
{
}



ConfigResponse::UP
FRTConfigRequestV3::createResponse(FRT_RPCRequest * request) const
{
    return ConfigResponse::UP(new FRTConfigResponseV3(request));
}

}
