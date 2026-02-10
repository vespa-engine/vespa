// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mirror_with_all.h"

#include <vespa/slobrok/sbmirror.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/transport.h>

namespace documentapi {

MirrorAndStuff::MirrorAndStuff(const slobrok::ConfiguratorFactory & config)
  : _transport(std::make_unique<FNET_Transport>()),
    _orb(std::make_unique<FRT_Supervisor>(_transport.get())),
    _mirror()
{
    _transport->Start();
    try {
        // If the configuration refers to a remote cluster that is unavailable, or if the
        // spec is somehow wrong, this may throw. We don't init this object in the ctor
        // list, as exception unwinding of the FRT_Supervisor will implicitly attempt to
        // sync against the underlying transport executor pool, which requires it to be
        // started _prior_.
        _mirror = std::make_unique<slobrok::api::MirrorAPI>(*_orb, config);
    } catch (...) {
        _transport->ShutDown(true);
        throw;
    }
}

MirrorAndStuff::~MirrorAndStuff() {
    _transport->ShutDown(true);
}

}
