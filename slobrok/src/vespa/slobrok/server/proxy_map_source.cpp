// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proxy_map_source.h"
#include <vespa/log/log.h>

LOG_SETUP(".slobrok.server.proxy_map_source");

namespace slobrok {

ProxyMapSource::ProxyMapSource() = default;

ProxyMapSource::~ProxyMapSource() = default;

void ProxyMapSource::registerListener(MapListener &listener) {
    _listeners.insert(&listener);
}

void ProxyMapSource::unregisterListener(MapListener &listener) {
    _listeners.erase(&listener);
}

void ProxyMapSource::add(const ServiceMapping &mapping) {
    for (auto * listener : _listeners) {
        listener->add(mapping);
    }
}

void ProxyMapSource::remove(const ServiceMapping &mapping) {
    for (auto * listener : _listeners) {
        listener->remove(mapping);
    }
}

void ProxyMapSource::update(const ServiceMapping &old_mapping,
                            const ServiceMapping &new_mapping)
{
    LOG_ASSERT(old_mapping.name == new_mapping.name);
    for (auto * listener : _listeners) {
        listener->update(old_mapping, new_mapping);
    }
}

} // namespace slobrok

