// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "cc-result.h"
#include <string>
#include <vespa/vespalib/util/count_down_latch.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/invoker.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>

namespace config::sentinel {

struct OutwardCheckContext {
    vespalib::CountDownLatch latch;
    std::string targetHostname;
    int targetPortnum;
    FRT_Supervisor &orb;
    OutwardCheckContext(size_t count,
                        const std::string &hostname,
                        int portnumber,
                        FRT_Supervisor &supervisor)
      : latch(count),
        targetHostname(hostname),
        targetPortnum(portnumber),
        orb(supervisor)
    {}
    ~OutwardCheckContext();
};

class OutwardCheck  : public FRT_IRequestWait {
private:
    CcResult _result = CcResult::UNKNOWN;
    FRT_Target *_target = nullptr;
    FRT_RPCRequest *_req = nullptr;
    std::string _spec;
    OutwardCheckContext &_context;
public:
    OutwardCheck(const std::string &spec, OutwardCheckContext &context, int ping_timeout);
    virtual ~OutwardCheck();
    void RequestDone(FRT_RPCRequest *req) override;
    bool ok() const { return _result == CcResult::ALL_OK; }
    CcResult result() const { return _result; }
    void classifyResult(CcResult value);
};

}
