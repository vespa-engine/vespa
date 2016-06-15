// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/fnet.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/slobrok/cfg.h>
#include <vespa/messagebus/network/oosmanager.h>
#include <vespa/vespalib/stllike/string.h>

namespace proton {

class Proton;

class OosCli : public FNET_Task
{
public:
    struct OosParams {
        Proton           &proton;
        vespalib::string  oos_server_pattern;
        vespalib::string  my_oos_name;
        config::ConfigUri slobrok_config;

        OosParams(Proton &p)
            : proton(p),
              oos_server_pattern("search/cluster.*/rtx/*/*"),
              my_oos_name(),
              slobrok_config(config::ConfigUri("admin/slobrok.0"))
        {}
    };
private:
    FRT_Supervisor        & _orb;
    OosParams               _params;
    slobrok::api::MirrorAPI _sbmirror;
    mbus::OOSManager        _oosmanager;
    int                     _curState;
public:
    OosCli(const OosParams &params, FRT_Supervisor &orb);
    virtual ~OosCli();
    virtual void PerformTask();
};

} // namespace proton

