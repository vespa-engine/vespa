// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/task.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/config/subscription/configuri.h>

class FRT_Supervisor;

namespace mbus { class OOSManager; }
namespace slobrok { namespace api { class IMirrorAPI; }}
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
    FRT_Supervisor                         & _orb;
    OosParams                                _params;
    std::unique_ptr<slobrok::api::IMirrorAPI> _sbmirror;
    std::unique_ptr<mbus::OOSManager>        _oosmanager;
    int                                      _curState;
public:
    OosCli(const OosParams &params, FRT_Supervisor &orb);
    virtual ~OosCli();
    virtual void PerformTask() override;
};

} // namespace proton

