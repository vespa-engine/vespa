// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "frtconfigrequest.h"
#include "protocol.h"
#include <vespa/vespalib/data/slime/slime.h>

class FRT_Values;
class FRT_RPCRequest;

namespace config {

class ConfigKey;
class Connection;
class Trace;
struct VespaVersion;

class SlimeConfigRequest : public FRTConfigRequest {
public:
    SlimeConfigRequest(Connection * connection,
                       const ConfigKey & key,
                       const vespalib::string & configMd5,
                       int64_t currentGeneration,
                       const vespalib::string & hostName,
                       int64_t serverTimeout,
                       const Trace & trace,
                       const VespaVersion & vespaVersion,
                       int64_t protocolVersion,
                       const CompressionType & compressionType,
                       const vespalib::string & methodName);
    ~SlimeConfigRequest() {}
    bool verifyState(const ConfigState & state) const override;
    virtual ConfigResponse::UP createResponse(FRT_RPCRequest * request) const override = 0;
private:
    void populateSlimeRequest(const ConfigKey & key,
                              const vespalib::string & configMd5,
                              int64_t currentGeneration,
                              const vespalib::string & hostName,
                              int64_t serverTimeout,
                              const Trace & trace,
                              const VespaVersion & vespaVersion,
                              int64_t protocolVersion,
                              const CompressionType & compressionType);
    static vespalib::string createJsonFromSlime(const vespalib::Slime & data);
    static const vespalib::string REQUEST_TYPES;
    vespalib::Slime _data;
};

}

