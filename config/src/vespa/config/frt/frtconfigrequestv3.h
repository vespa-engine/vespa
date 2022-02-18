// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "slimeconfigrequest.h"

class FRT_Values;
class FRT_RPCRequest;

namespace config {

class ConfigKey;
class Connection;
class Trace;
struct VespaVersion;

class FRTConfigRequestV3 : public SlimeConfigRequest {
public:
    FRTConfigRequestV3(Connection * connection,
                       const ConfigKey & key,
                       const vespalib::string & configXxhash64,
                       int64_t currentGeneration,
                       const vespalib::string & hostName,
                       duration serverTimeout,
                       const Trace & trace,
                       const VespaVersion & vespaVersion,
                       const CompressionType & compressionType);
    std::unique_ptr<ConfigResponse> createResponse(FRT_RPCRequest * request) const override;
};

}

