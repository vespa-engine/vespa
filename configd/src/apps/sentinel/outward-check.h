// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "status-callback.h"
#include "peer-check.h"
#include <vespa/fnet/frt/supervisor.h>

namespace config::sentinel {

class OutwardCheck : public StatusCallback {
    bool _wasOk = false;
    bool _wasBad = false;
    PeerCheck _check;
public:
    OutwardCheck(const std::string &hostname, int portnumber, FRT_Supervisor &orb)
      : _check(*this, hostname, portnumber, orb)
    {}
    virtual ~OutwardCheck();
    bool ok() const { return _wasOk; }
    bool bad() const { return _wasBad; }
    void returnStatus(bool ok) override {
        if (ok) {
            _wasBad = false;
            _wasOk = true;
        } else {
            _wasOk = false;
            _wasBad = true;
        }
    }
};

}
