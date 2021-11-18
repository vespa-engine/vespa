// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <cstdint>

namespace searchcorespi::index { struct IThreadingService; }

namespace proton::documentmetastore {

struct IStore;

/**
 * This class delays reuse of lids until references to the lids have
 * been purged from the data structures in memory index and attribute
 * vectors.
 *
 * Note that an additional delay is added by the IStore component,
 * where lids are put on a hold list to ensure that queries started
 * before lid was purged also blocks reuse of lid.
 *
 * Currently only works correctly when visibility delay is 0.
 */
class LidReuseDelayer
{
    searchcorespi::index::IThreadingService &_writeService;
    IStore &_documentMetaStore;
    std::vector<uint32_t> _pendingLids; // lids waiting for commit

public:
    LidReuseDelayer(searchcorespi::index::IThreadingService &writeService, IStore &documentMetaStore);
    ~LidReuseDelayer();
    void delayReuse(uint32_t lid);
    void delayReuse(const std::vector<uint32_t> &lids);
    std::vector<uint32_t> getReuseLids();
};

}
