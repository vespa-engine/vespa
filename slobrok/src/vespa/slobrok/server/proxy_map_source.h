// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "map_source.h"
#include <set>

namespace slobrok {

/**
 * Proof-of-concept implementation of MapSource broadcasting.
 **/
class ProxyMapSource : public MapSource, public MapListener {
    std::set<MapListener *> _listeners;
    void registerListener(MapListener &listener) override;
    void unregisterListener(MapListener &listener) override;
public:
    ProxyMapSource();
     ~ProxyMapSource();

    void add(const ServiceMapping &mapping) override;
    void remove(const ServiceMapping &mapping) override;
    void update(const ServiceMapping &old_mapping,
                const ServiceMapping &new_mapping) override;
};

} // namespace slobrok

