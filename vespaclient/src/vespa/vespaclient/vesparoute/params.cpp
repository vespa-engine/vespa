// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "params.h"

namespace vesparoute {

Params::Params() :
    _rpcParams(),
    _hops(),
    _routes(),
    _documentTypesConfigId("client"),
    _routingConfigId("client"),
    _protocol("document"),
    _lstHops(false),
    _lstRoutes(false),
    _lstServices(false),
    _dump(false),
    _verify(false)
{
    _rpcParams.setOOSServerPattern("search/*/rtx/*/clustercontroller"); // magic
}

Params::~Params()
{
    // empty
}

}

