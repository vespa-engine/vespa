// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "params.h"

namespace vesparoute {

Params::Params() :
    _rpcParams(),
    _hops(),
    _routes(),
    _documentTypesConfigId("client"),
    _routingConfigId("client"),
    _protocol("document"),
    _slobrokConfigId(""),
    _lstHops(false),
    _lstRoutes(false),
    _lstServices(false),
    _dump(false),
    _verify(false)
{}

Params::~Params() = default;

}

