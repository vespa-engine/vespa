// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "map_listener.h"
#include "map_source.h"
#include "proxy_map_source.h"

#include <map>
#include <vector>

namespace slobrok {

/**
 * Listens to events from multiple maps and publishes the union of them.
 **/
class UnionServiceMap
    : public MapSource, public MapListener
{
private:
    struct CountedSpec {
        vespalib::string spec;
        size_t count;
    };
    using Mappings = std::vector<CountedSpec>;
    std::map<vespalib::string, Mappings> _mappings;
    ProxyMapSource _proxy;
public:
    UnionServiceMap();
    virtual ~UnionServiceMap();

    void add(const ServiceMapping &mapping) override;
    void remove(const ServiceMapping &mapping) override;
    void update(const ServiceMapping &old_mapping,
                const ServiceMapping &new_mapping) override;

    void registerListener(MapListener &listener) override;
    void unregisterListener(MapListener &listener) override;
};

} // namespace slobrok

