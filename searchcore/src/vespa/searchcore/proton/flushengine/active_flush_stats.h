// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/time.h>
#include <optional>

namespace proton::flushengine {

/**
 * Tracks the oldest start time of active (ongoing) flushes in each flush handler.
 */
class ActiveFlushStats {
public:
    using OptionalTime = std::optional<vespalib::system_time>;

private:
    using StatsMap = vespalib::hash_map<vespalib::string, vespalib::system_time>;
    StatsMap _stats;

public:
    ActiveFlushStats();
    /**
     * Set the start time for a flush in the given flush handler.
     * A start time is only updated if it is older than the current oldest one.
     */
    void set_start_time(const vespalib::string& handler_name, vespalib::system_time start_time);
    OptionalTime oldest_start_time(const vespalib::string& handler_name) const;
};

}

