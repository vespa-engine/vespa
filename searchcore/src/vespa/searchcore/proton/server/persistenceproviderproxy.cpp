// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.persistencproviderproxy");

#include "persistenceproviderproxy.h"

using storage::spi::PersistenceProvider;

namespace proton {

PersistenceProviderProxy::PersistenceProviderProxy(PersistenceProvider &pp)
    : _pp(pp)
{
}

} // namespace proton
