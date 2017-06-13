// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tls_stats_map.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace proton {
namespace flushengine {

TlsStatsMap::TlsStatsMap(Map &&map)
    : _map(std::move(map))
{ }

TlsStatsMap::~TlsStatsMap() { }

const TlsStats &
TlsStatsMap::getTlsStats(const vespalib::string &domain) const {
    auto itr = _map.find(domain);
    if (itr != _map.end()) {
        return itr->second;
    }
    abort();
}

}
}

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, proton::flushengine::TlsStats);
