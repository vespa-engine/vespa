// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "externslobrokpolicy.h"
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/messagebus/routing/routingcontext.h>
#include <vespa/fnet/frt/frt.h>
#include <vespa/slobrok/sbmirror.h>

using slobrok::api::IMirrorAPI;
using slobrok::api::MirrorAPI;

namespace documentapi {

ExternSlobrokPolicy::ExternSlobrokPolicy(const std::map<string, string>& param)
    : AsyncInitializationPolicy(param),
      _firstTry(true),
      _orb(std::make_unique<FRT_Supervisor>()),
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
    bool started = _mirror.get() != NULL;
    _mirror.reset();
    if (started) {
        _orb->ShutDown(true);
    }
}

string ExternSlobrokPolicy::init() {
    if (_slobroks.size() != 0) {
        slobrok::ConfiguratorFactory config(_slobroks);
        _mirror.reset(new MirrorAPI(*_orb, config));
    } else if (_configSources.size() != 0) {
        slobrok::ConfiguratorFactory config(
            config::ConfigUri(_slobrokConfigId,
                              config::IConfigContext::SP(
                                  new config::ConfigContext(config::ServerSpec(_configSources)))));
        _mirror.reset(new MirrorAPI(*_orb, config));
    }

    if (_mirror.get()) {
        _orb->Start();
    }

    return "";
}

IMirrorAPI::SpecList
ExternSlobrokPolicy::lookup(mbus::RoutingContext& context, const string& pattern) {
    vespalib::LockGuard guard(_lock);

    const IMirrorAPI& mirror(_mirror.get()? *_mirror : context.getMirror());

    IMirrorAPI::SpecList entries = mirror.lookup(pattern);

    if (_firstTry) {
        int count = 0;
        while (entries.empty() && count < 100) {
            FastOS_Thread::Sleep(50);
            entries = mirror.lookup(pattern);
            count++;
        }
    }

    _firstTry = false;

    return entries;
}

}
