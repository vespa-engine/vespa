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
                                       const vespalib::string & configXxhash64,
                                       int64_t currentGeneration,
                                       const vespalib::string & hostName,
                                       vespalib::duration serverTimeout,
                                       const Trace & trace,
                                       const VespaVersion & vespaVersion,
                                       const CompressionType & compressionType)
    : SlimeConfigRequest(connection, key, configXxhash64, currentGeneration, hostName, serverTimeout, trace, vespaVersion, 3, compressionType, "config.v3.getConfig")
{
}



std::unique_ptr<ConfigResponse>
FRTConfigRequestV3::createResponse(FRT_RPCRequest * request) const
{
    return std::make_unique<FRTConfigResponseV3>(request);
}

}
