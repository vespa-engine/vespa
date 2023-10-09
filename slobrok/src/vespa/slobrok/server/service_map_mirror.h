// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "service_mapping.h"
#include "map_diff.h"
#include "map_source.h"
#include <vespa/vespalib/util/gencnt.h>
#include <map>
#include <set>

namespace slobrok {

/**
 * @class ServiceMapMirror
 * @brief Holds a name->spec map which can be incrementally updated
 **/
class ServiceMapMirror : public MapSource
{
public:
    using Generation = vespalib::GenCnt;

    ServiceMapMirror();
    ~ServiceMapMirror();

    /** update according to diff */
    void apply(const MapDiff &diff);

    /** remove all mappings */
    void clear();

    const Generation &currentGeneration() const { return _currGen; }

    ServiceMappingList allMappings() const;

private:
    void registerListener(MapListener &listener) override;
    void unregisterListener(MapListener &listener) override;

    using Map = std::map<vespalib::string, vespalib::string>;
    Map _map;
    Generation _currGen;
    std::set<MapListener *> _listeners;
};

//-----------------------------------------------------------------------------

} // namespace slobrok

