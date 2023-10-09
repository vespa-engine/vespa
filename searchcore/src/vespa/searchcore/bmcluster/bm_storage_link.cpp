// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_storage_link.h"
#include "pending_tracker.h"

namespace search::bmcluster {


BmStorageLink::BmStorageLink()
    : storage::StorageLink("vespa-bm-feed"),
      StorageReplyErrorChecker(),
      _pending_hash()
{
}

BmStorageLink::~BmStorageLink() = default;

bool
BmStorageLink::onDown(const std::shared_ptr<storage::api::StorageMessage>& msg)
{
    (void) msg;
    return false;
}

bool
BmStorageLink::onUp(const std::shared_ptr<storage::api::StorageMessage>& msg)
{
    auto tracker = _pending_hash.release(msg->getMsgId());
    if (tracker != nullptr) {
        check_error(*msg);
        tracker->release();
        return true;
    }
    return false;
}

}
