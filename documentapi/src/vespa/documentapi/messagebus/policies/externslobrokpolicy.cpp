// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "externslobrokpolicy.h"
#include <vespa/messagebus/routing/routingcontext.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/transport.h>
#include <vespa/fastos/thread.h>
#include <thread>

using slobrok::api::IMirrorAPI;
using slobrok::api::MirrorAPI;

namespace documentapi {

ExternSlobrokPolicy::ExternSlobrokPolicy(const std::map<string, string>& param)
    : AsyncInitializationPolicy(param),
      _firstTry(true),
      _threadPool(std::make_unique<FastOS_ThreadPool>(1024*60)),
      _transport(std::make_unique<FNET_Transport>()),
      _orb(std::make_unique<FRT_Supervisor>(_transport.get())),
      _slobrokConfigId("admin/slobrok.0")
{
    if (param.find("config") != param.end()) {
       vespalib::StringTokenizer configServers(param.find("config")->second, ",");
        for (uint32_t j = 0; j < configServers.size(); j++) {
            _configSources.push_back(configServers[j]);
        }
    }

    if (param.find("slobroks") != param.end()) {
        vespalib::StringTokenizer slobrokList(param.find("slobroks")->second, ",");
        for (uint32_t j = 0; j < slobrokList.size(); j++) {
            _slobroks.push_back(slobrokList[j]);
        }
    }

    if (param.find("slobrokconfigid") != param.end()) {
        _slobrokConfigId = param.find("slobrokconfigid")->second;
    }

    if (_slobroks.size() || _configSources.size()) {
        needAsynchronousInit();
    }
}

ExternSlobrokPolicy::~ExternSlobrokPolicy()
{
    bool started = (bool)_mirror;
    _mirror.reset();
    if (started) {
        _transport->ShutDown(true);
    }
}

string ExternSlobrokPolicy::init() {
    if (_slobroks.size() != 0) {
        slobrok::ConfiguratorFactory config(_slobroks);
        _mirror.reset(new MirrorAPI(*_orb, config));
    } else if (_configSources.size() != 0) {
        slobrok::ConfiguratorFactory config(
            config::ConfigUri(_slobrokConfigId,
                             std::make_shared<config::ConfigContext>(config::ServerSpec(_configSources))));
        _mirror = std::make_unique<MirrorAPI>(*_orb, config);
    }

    if (_mirror.get()) {
        _transport->Start(_threadPool.get());
    }

    return "";
}

IMirrorAPI::SpecList
ExternSlobrokPolicy::lookup(mbus::RoutingContext& context, const string& pattern) {
    std::lock_guard guard(_lock);

    const IMirrorAPI& mirror(_mirror.get()? *_mirror : context.getMirror());

    IMirrorAPI::SpecList entries = mirror.lookup(pattern);

    if (_firstTry) {
        int count = 0;
        while (entries.empty() && count < 100) {
            std::this_thread::sleep_for(50ms);
            entries = mirror.lookup(pattern);
            count++;
        }
    }

    _firstTry = false;

    return entries;
}

}
