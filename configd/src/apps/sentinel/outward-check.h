// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <string>
#include <vespa/vespalib/util/count_down_latch.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/invoker.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>

namespace config::sentinel {

class OutwardCheck  : public FRT_IRequestWait {
private:
    bool _wasOk = false;
    bool _wasBad = false;
    FRT_Target *_target = nullptr;
    FRT_RPCRequest *_req = nullptr;
    std::string _spec;
    vespalib::CountDownLatch &_countDownLatch;
public:
    OutwardCheck(const std::string &spec, const char * myHostname, int myPortnum,
                 FRT_Supervisor &orb, vespalib::CountDownLatch &latch);
    virtual ~OutwardCheck();
    void RequestDone(FRT_RPCRequest *req) override;
    bool ok() const { return _wasOk; }
    bool bad() const { return _wasBad; }
};

}
