// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistenceproviderproxy.h"

using storage::spi::PersistenceProvider;

namespace proton {

PersistenceProviderProxy::PersistenceProviderProxy(PersistenceProvider &pp)
    : _pp(pp)
{
}

} // namespace proton
