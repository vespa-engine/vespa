// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_component_config_producer.h"

namespace vespalib {

SimpleComponentConfigProducer::SimpleComponentConfigProducer()
    : _lock(),
      _state()
{
}

SimpleComponentConfigProducer::~SimpleComponentConfigProducer() = default;

void
SimpleComponentConfigProducer::addConfig(const Config &config)
{
    std::lock_guard guard(_lock);
    _state.insert(std::make_pair(config.name, config)).first->second = config;
}

void
SimpleComponentConfigProducer::removeConfig(const vespalib::string &name)
{
    std::lock_guard guard(_lock);
    _state.erase(name);
}

void
SimpleComponentConfigProducer::getComponentConfig(Consumer &consumer)
{
    std::lock_guard guard(_lock);
    for (const auto & entry : _state) {
        consumer.add(entry.second);
    }
}

} // namespace vespalib
