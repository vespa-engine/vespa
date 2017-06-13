// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configshim.h"

namespace slobrok {

ConfigShim::ConfigShim(uint32_t port)
    : _port(port), _statePort(0), _configId(""),
      _factory(config::ConfigUri::createEmpty())
{}

ConfigShim::ConfigShim(uint32_t port, uint32_t statePort_in, const std::string& cfgId)
    : _port(port),
      _statePort(statePort_in),
      _configId(cfgId),
      _factory(config::ConfigUri(_configId))
{}

ConfigShim::ConfigShim(uint32_t port, const std::string& cfgId, config::IConfigContext::SP cfgCtx)
    : _port(port),
      _statePort(0),
      _configId(cfgId),
      _factory(config::ConfigUri(cfgId, cfgCtx))
{}

ConfigShim::~ConfigShim() {}

}
