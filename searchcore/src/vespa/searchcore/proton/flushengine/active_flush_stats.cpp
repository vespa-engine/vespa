// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "active_flush_stats.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/time.h>

namespace proton::flushengine {

ActiveFlushStats::ActiveFlushStats()
    : _stats()
{
}

void
ActiveFlushStats::set_start_time(const vespalib::string& handler_name, vespalib::system_time start_time)
{
    auto itr = _stats.find(handler_name);
    if (itr != _stats.end()) {
        if (start_time < itr->second) {
            itr->second = start_time;
        }
    } else {
        _stats.insert(std::make_pair(handler_name, start_time));
    }
}

ActiveFlushStats::OptionalTime
ActiveFlushStats::oldest_start_time(const vespalib::string& handler_name) const
{
    auto itr = _stats.find(handler_name);
    if (itr != _stats.end()) {
        return OptionalTime(itr->second);
    }
    return std::nullopt;
}

}

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, vespalib::system_time);

