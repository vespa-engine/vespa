// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lidreusedelayer.h"
#include "i_store.h"
#include <vespa/searchcorespi/index/ithreadingservice.h>

namespace proton::documentmetastore {

using searchcorespi::index::IThreadingService;

LidReuseDelayer::LidReuseDelayer(IThreadingService &writeService, IStore &documentMetaStore)
    : _writeService(writeService),
      _documentMetaStore(documentMetaStore),
      _pendingLids()
{
}

LidReuseDelayer::~LidReuseDelayer() {
    assert(_pendingLids.empty());
}

bool
LidReuseDelayer::delayReuse(uint32_t lid)
{
    assert(_writeService.master().isCurrentThread());
    if ( ! _documentMetaStore.getFreeListActive())
        return false;
    _pendingLids.push_back(lid);
    return false;
}

bool
LidReuseDelayer::delayReuse(const std::vector<uint32_t> &lids)
{
    assert(_writeService.master().isCurrentThread());
    if ( ! _documentMetaStore.getFreeListActive() || lids.empty())
        return false;
    _pendingLids.insert(_pendingLids.end(), lids.cbegin(), lids.cend());
    return false;
}

std::vector<uint32_t>
LidReuseDelayer::getReuseLids()
{
    std::vector<uint32_t> result;
    assert(_writeService.master().isCurrentThread());
    result = _pendingLids;
    _pendingLids.clear();
    return result;
}

}

