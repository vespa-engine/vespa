// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tls_stats_factory.h"
#include "tls_stats_map.h"
#include <vespa/searchlib/transactionlog/translogserver.h>

using search::transactionlog::DomainInfo;
using search::transactionlog::DomainStats;

namespace proton::flushengine {

TlsStatsFactory::TlsStatsFactory(TransLogServer::SP tls)
    : _tls(tls)
{
}

TlsStatsFactory::~TlsStatsFactory()
{
}

TlsStatsMap
TlsStatsFactory::create()
{
    DomainStats stats  = _tls->getDomainStats();
    TlsStatsMap::Map map;
    for (auto &itr : stats) {
        const DomainInfo &di = itr.second;
        map.insert(std::make_pair(itr.first,
                                  TlsStats(di.byteSize,
                                           di.range.from(),
                                           di.range.to())));
    }
    return TlsStatsMap(std::move(map));
}

}
