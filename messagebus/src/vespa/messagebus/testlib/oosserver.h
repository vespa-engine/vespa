// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/sync.h>
#include <vespa/slobrok/sbregister.h>
#include <vespa/fnet/frt/supervisor.h>
#include <string>
#include <vector>
#include "oosstate.h"

namespace mbus {

class Slobrok;

class OOSServer : public FRT_Invokable
{
private:
    OOSServer(const OOSServer &);
    OOSServer &operator=(const OOSServer &);

    vespalib::Lock            _lock;
    FRT_Supervisor            _orb;
    int                       _port;
    slobrok::api::RegisterAPI _regAPI;
    uint32_t                  _genCnt;
    std::vector<string>  _state;

public:
    OOSServer(const Slobrok &slobrok, const string service,
              const OOSState &state = OOSState());
    ~OOSServer();
    int port() const;
    void rpc_poll(FRT_RPCRequest *req);
    void setState(const OOSState &state);
};

} // namespace mbus

