// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "tls_stats.h"

#include <vespa/vespalib/stllike/hash_map.h>

namespace proton::flushengine {

/**
 * Class representing statistics for a transaction log server over multiple
 * domains.
 */
class TlsStatsMap {
public:
    using Map = vespalib::hash_map<std::string, TlsStats>;

private:
    Map _map;

public:
    TlsStatsMap(Map&& map);
    ~TlsStatsMap();

    const TlsStats& getTlsStats(const std::string& domain) const;
};

} // namespace proton::flushengine
