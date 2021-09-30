// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rpcsend.h"

namespace mbus {

class RPCSendV1 : public RPCSend {
public:
    static bool isCompatible(vespalib::stringref method, vespalib::stringref request, vespalib::stringref respons);
private:
    void build(FRT_ReflectionBuilder & builder) override;
    const char * getReturnSpec() const override;
    std::unique_ptr<Params> toParams(const FRT_Values &param) const override;
    void encodeRequest(FRT_RPCRequest &req, const vespalib::Version &version, const Route & route,
                       const RPCServiceAddress & address, const Message & msg, uint32_t traceLevel,
                       const PayLoadFiller &filler, duration timeRemaining) const override;

    std::unique_ptr<Reply> createReply(const FRT_Values & response, const string & serviceName,
                                       Error & error, vespalib::Trace & trace) const override;
    void createResponse(FRT_Values & ret, const string & version, Reply & reply, Blob payload) const override;
};

} // namespace mbus
