// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mirror_with_all.h"

#include <vespa/slobrok/sbmirror.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/transport.h>

namespace documentapi {

MirrorAndStuff::MirrorAndStuff(const slobrok::ConfiguratorFactory & config)
  : _transport(std::make_unique<FNET_Transport>()),
    _orb(std::make_unique<FRT_Supervisor>(_transport.get())),
    _mirror(std::make_unique<slobrok::api::MirrorAPI>(*_orb, config))
{
    _transport->Start();
}

MirrorAndStuff::~MirrorAndStuff() {
    _transport->ShutDown(true);
}

}
