// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_storage_link.h"
#include "pending_tracker.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace feedbm {


void
BmStorageLink::retain(uint64_t msg_id, PendingTracker &tracker)
{
    tracker.retain();
    std::lock_guard lock(_mutex);
    _pending.insert(std::make_pair(msg_id, &tracker));
}

PendingTracker *
BmStorageLink::release(uint64_t msg_id)
{
    std::lock_guard lock(_mutex);
    auto itr = _pending.find(msg_id);
    if (itr == _pending.end()) {
        return nullptr;
    }
    auto tracker = itr->second;
    _pending.erase(itr);
    return tracker;
}

BmStorageLink::BmStorageLink()
    : storage::StorageLink("vespa-bm-feed"),
      StorageReplyErrorChecker(),
      _mutex(),
      _pending()
{
}

BmStorageLink::~BmStorageLink()
{
    std::lock_guard lock(_mutex);
    assert(_pending.empty());
}

bool
BmStorageLink::onDown(const std::shared_ptr<storage::api::StorageMessage>& msg)
{
    (void) msg;
    return false;
}

bool
BmStorageLink::onUp(const std::shared_ptr<storage::api::StorageMessage>& msg)
{
    auto tracker = release(msg->getMsgId());
    if (tracker != nullptr) {
        check_error(*msg);
        tracker->release();
        return true;
    }
    return false;
}

}
