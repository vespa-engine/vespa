// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "frtconfigrequest.h"
#include "protocol.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/time.h>


class FRT_Values;
class FRT_RPCRequest;

namespace config {

class ConfigKey;
class Connection;
class Trace;
struct VespaVersion;

class SlimeConfigRequest : public FRTConfigRequest {
public:
    using duration = vespalib::duration;
    SlimeConfigRequest(Connection * connection,
                       const ConfigKey & key,
                       const std::string & configXxhash64,
                       int64_t currentGeneration,
                       const std::string & hostName,
                       duration serverTimeout,
                       const Trace & trace,
                       const VespaVersion & vespaVersion,
                       int64_t protocolVersion,
                       const CompressionType & compressionType,
                       const std::string & methodName);
    ~SlimeConfigRequest();
    bool verifyState(const ConfigState & state) const override;
    std::unique_ptr<ConfigResponse> createResponse(FRT_RPCRequest * request) const override = 0;
private:
    void populateSlimeRequest(const ConfigKey & key,
                              const std::string & configXxhash64,
                              int64_t currentGeneration,
                              const std::string & hostName,
                              duration serverTimeout,
                              const Trace & trace,
                              const VespaVersion & vespaVersion,
                              int64_t protocolVersion,
                              const CompressionType & compressionType);
    static std::string createJsonFromSlime(const vespalib::Slime & data);
    vespalib::Slime _data;
};

}

