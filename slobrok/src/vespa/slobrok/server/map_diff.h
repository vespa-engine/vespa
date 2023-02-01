// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/gencnt.h>
#include "service_mapping.h"

namespace slobrok {

/**
 * represents an incremental update to a name->spec map,
 * or optionally a full dump of it.
 **/
struct MapDiff {
    /** construct incremental diff */
    MapDiff(const vespalib::GenCnt &from,
            std::vector<vespalib::string> remove,
            ServiceMappingList update,            
            const vespalib::GenCnt &to)
      : fromGen(from),
        removed(std::move(remove)),
        updated(std::move(update)),
        toGen(to)
    {}

    /** construct full map dump */
    MapDiff(ServiceMappingList mappings,
            const vespalib::GenCnt &to)
      : MapDiff(0, {}, std::move(mappings), to)
    {}

    MapDiff(MapDiff &&) noexcept;
    ~MapDiff();
    
    // is this a diff from the empty map:
    bool is_full_dump() const { return fromGen == vespalib::GenCnt(0); }

    // which generation this diff goes from:
    vespalib::GenCnt fromGen;

    // names to remove (empty if is_full_dump):
    std::vector<vespalib::string> removed;

    // name->spec pairs to add or update:
    ServiceMappingList updated;

    // which generation this diff brings you to:
    vespalib::GenCnt toGen;
};

} // namespace slobrok

