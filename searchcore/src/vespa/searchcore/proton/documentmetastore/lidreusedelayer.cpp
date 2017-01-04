// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.documentmetastore.lidreusedelayer");
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/closuretask.h>
#include "i_store.h"
#include "lidreusedelayer.h"

namespace proton
{

namespace documentmetastore
{

using searchcorespi::index::IThreadingService;
using vespalib::makeClosure;
using vespalib::makeTask;

LidReuseDelayer::LidReuseDelayer(IThreadingService &writeService,
                                 IStore &documentMetaStore)
    : _writeService(writeService),
      _documentMetaStore(documentMetaStore),
      _immediateCommit(true),
      _hasIndexedOrAttributeFields(false),
      _pendingLids()
{
}


LidReuseDelayer::~LidReuseDelayer()
{
}


bool
LidReuseDelayer::delayReuse(uint32_t lid)
{
    assert(_writeService.master().isCurrentThread());
    if (!_documentMetaStore.getFreeListActive())
        return false;
    if (!_immediateCommit) {
        _pendingLids.push_back(lid);
        return false;
    }
    if (!_hasIndexedOrAttributeFields) {
        _documentMetaStore.removeComplete(lid);
        return false;
    }
    return true;
}


bool
LidReuseDelayer::delayReuse(const std::vector<uint32_t> &lids)
{
    assert(_writeService.master().isCurrentThread());
    if (!_documentMetaStore.getFreeListActive() || lids.empty())
        return false;
    if (!_immediateCommit) {
        _pendingLids.insert(_pendingLids.end(), lids.cbegin(), lids.cend());
        return false;
    }
    if (!_hasIndexedOrAttributeFields) {
        _documentMetaStore.removeBatchComplete(lids);
        return false;
    }
    return true;
}


void
LidReuseDelayer::setImmediateCommit(bool immediateCommit)
{
    assert(_pendingLids.empty());
    _immediateCommit = immediateCommit;
}


bool
LidReuseDelayer::getImmediateCommit() const
{
    return _immediateCommit;
}


void
LidReuseDelayer::setHasIndexedOrAttributeFields(bool hasIndexedOrAttributeFields)
{
    assert(_pendingLids.empty());
    _hasIndexedOrAttributeFields = hasIndexedOrAttributeFields;
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

}
