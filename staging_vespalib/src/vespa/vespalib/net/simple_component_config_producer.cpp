// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_component_config_producer.h"

namespace vespalib {

SimpleComponentConfigProducer::SimpleComponentConfigProducer()
    : _lock(),
      _state()
{
}

void
SimpleComponentConfigProducer::addConfig(const Config &config)
{
    LockGuard guard(_lock);
    _state.insert(std::make_pair(config.name, config)).first->second = config;
}

void
SimpleComponentConfigProducer::removeConfig(const vespalib::string &name)
{
    LockGuard guard(_lock);
    _state.erase(name);
}

void
SimpleComponentConfigProducer::getComponentConfig(Consumer &consumer)
{
    typedef std::map<vespalib::string, Config>::const_iterator ITR;
    LockGuard guard(_lock);
    for (ITR itr = _state.begin(); itr != _state.end(); ++itr) {
        consumer.add(itr->second);
    }
}

} // namespace vespalib
