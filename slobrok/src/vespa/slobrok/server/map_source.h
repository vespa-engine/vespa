// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "map_listener.h"

namespace slobrok {

/**
 * Interface for sources of incremental map updates.
 **/
struct MapSource {
    virtual void registerListener(MapListener &listener) = 0;
    virtual void unregisterListener(MapListener &listener) = 0;
    virtual ~MapSource();
};

} // namespace slobrok

