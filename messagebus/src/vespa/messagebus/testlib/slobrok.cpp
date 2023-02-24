// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slobrok.h"
#include <vespa/slobrok/server/sbenv.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".slobrok");

namespace mbus {

void
Slobrok::init()
{
    slobrok::ConfigShim shim(_port);
    _env = std::make_unique<slobrok::SBEnv>(shim);
    _thread = std::thread([env = _env.get()]()
                          {
                              if (env->MainLoop() != 0) {
                                  LOG_ABORT("Slobrok main failed");
                              }
                          });
    _env->getTransport()->sync();
    int p = _env->getSupervisor()->GetListenPort();
    LOG_ASSERT(p != 0 && (p == _port || _port == 0));
    _port = p;
}

Slobrok::Slobrok()
  : _env(),
    _port(0),
    _thread()
{
    init();
}

Slobrok::Slobrok(int p)
  : _env(),
    _port(p),
    _thread()
{
    init();
}

Slobrok::~Slobrok()
{
    _env->getTransport()->ShutDown(true);
    _thread.join();
}

int
Slobrok::port() const
{
    return _port;
}

config::ConfigUri
Slobrok::config() const
{
    cloud::config::SlobroksConfigBuilder builder;
    cloud::config::SlobroksConfig::Slobrok sb;
    sb.connectionspec = vespalib::make_string("tcp/localhost:%d", port());
    builder.slobrok.push_back(sb);
    return config::ConfigUri::createFromInstance(builder);
}

} // namespace mbus
