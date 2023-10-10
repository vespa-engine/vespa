// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "map_listener.h"
#include <memory>

namespace slobrok {

struct MapSource;

class MapSubscription {
private:
    MapSource &_source;
    MapListener &_listener;
    struct Tag {};
public:
    MapSubscription(MapSource &source, MapListener &listener, Tag);

    MapSubscription(const MapSubscription &) = delete;
    MapSubscription(MapSubscription &&) = delete;
    MapSubscription& operator=(const MapSubscription &) = delete;
    MapSubscription& operator=(MapSubscription &&) = delete;

    ~MapSubscription();

    static std::unique_ptr<MapSubscription> subscribe(MapSource &source, MapListener &listener);
};

/**
 * Interface for sources of incremental map updates.
 **/
struct MapSource {
    virtual ~MapSource();
private:
    friend class MapSubscription;
    virtual void registerListener(MapListener &listener) = 0;
    virtual void unregisterListener(MapListener &listener) = 0;
};

} // namespace slobrok

