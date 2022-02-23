// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "externslobrokpolicy.h"
#include "mirror_with_all.h"
#include <vespa/messagebus/routing/routingcontext.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/transport.h>
#include <vespa/fastos/thread.h>
#include <thread>

using slobrok::api::IMirrorAPI;
using slobrok::api::MirrorAPI;
using slobrok::ConfiguratorFactory;

namespace documentapi {

ExternSlobrokPolicy::ExternSlobrokPolicy(const std::map<string, string>& param)
    : AsyncInitializationPolicy(param),
      _firstTry(true),
      _mirrorWithAll(),
      _slobrokConfigId("client")
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

const IMirrorAPI*
ExternSlobrokPolicy::getMirror() const {
    return _mirrorWithAll ? _mirrorWithAll->mirror() : nullptr;
}

ExternSlobrokPolicy::~ExternSlobrokPolicy() = default;

string
ExternSlobrokPolicy::init() {
    std::lock_guard guard(_lock);
    if (_slobroks.size() != 0) {
        ConfiguratorFactory config(_slobroks);
        _mirrorWithAll = std::make_unique<MirrorAndStuff>(config);
    } else if (_configSources.size() != 0) {
        ConfiguratorFactory config(
                config::ConfigUri(_slobrokConfigId,
                                  std::make_shared<config::ConfigContext>(config::ServerSpec(_configSources))));
        _mirrorWithAll = std::make_unique<MirrorAndStuff>(config);
    }

    return "";
}

IMirrorAPI::SpecList
ExternSlobrokPolicy::lookup(mbus::RoutingContext& context, const string& pattern) {
    std::lock_guard guard(_lock);

    const IMirrorAPI * myMirror = getMirror();
    const IMirrorAPI& mirror(myMirror ? *myMirror : context.getMirror());

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
