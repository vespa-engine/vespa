// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "map_source.h"

namespace slobrok {

MapSubscription::MapSubscription(MapSource &source, MapListener &listener, Tag)
  : _source(source), _listener(listener)
{
    _source.registerListener(_listener);
}

MapSubscription::~MapSubscription() {
    _source.unregisterListener(_listener);
}

std::unique_ptr<MapSubscription>
MapSubscription::subscribe(MapSource &source, MapListener &listener)
{
    return std::make_unique<MapSubscription>(source, listener, Tag{});
}


MapSource::~MapSource() = default;


} // namespace slobrok

