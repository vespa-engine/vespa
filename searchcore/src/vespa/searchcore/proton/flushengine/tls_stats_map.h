// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include "tls_stats.h"

namespace proton::flushengine {

/**
 * Class representing statistics for a transaction log server over multiple
 * domains.
 */
class TlsStatsMap
{
public:
    using Map = vespalib::hash_map<vespalib::string, TlsStats>;
private:
    Map _map;

public:
    TlsStatsMap(Map &&map);
    ~TlsStatsMap();

    const TlsStats &getTlsStats(const vespalib::string &domain) const;
};

}
