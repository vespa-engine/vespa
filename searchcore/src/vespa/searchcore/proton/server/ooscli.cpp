// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ooscli.h"
#include "proton.h"
#include <vespa/slobrok/sbmirror.h>
#include <vespa/messagebus/network/oosmanager.h>
#include <vespa/fnet/frt/supervisor.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.ooscli");

namespace proton {

OosCli::OosCli(const OosParams &params, FRT_Supervisor &orb)
  : FNET_Task(orb.GetScheduler()),
    _orb(orb),
    _params(params),
    _sbmirror(std::make_unique<slobrok::api::MirrorAPI>(_orb, params.slobrok_config)),
    _oosmanager(std::make_unique<mbus::OOSManager>(_orb, *_sbmirror, params.oos_server_pattern)),
    _curState(-1)
{
    Schedule(0.1);
}


OosCli::~OosCli() {
    Kill(); // unschedule task
}

void
OosCli::PerformTask()
{
    int old = _curState;
    if (_oosmanager->isOOS(_params.my_oos_name)) {
        _params.proton.getMatchEngine().setOutOfService();
        _curState = 0;
        if (_curState != old) {
            LOG(warning, "this search engine (messagebus name '%s') is Out Of Service",
                _params.my_oos_name.c_str());
        }
    } else {
        if (_params.proton.isReplayDone()) {
            _params.proton.getMatchEngine().setInService();
            _curState = 1;
        }
        if (_curState != old) {
            LOG(info, "search engine is In Service, setting online");
        }
    }
    Schedule(1.0);
}

} // namespace proton
