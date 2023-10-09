// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "storage_reply_error_checker.h"
#include "pending_tracker_hash.h"
#include <vespa/storage/common/storagelink.h>

namespace search::bmcluster {

class PendingTracker;

/*
 * Storage link used to feed storage api messages to a distributor or
 * service layer node. A count of error replies is maintained.
 */
class BmStorageLink : public storage::StorageLink,
                      public StorageReplyErrorChecker
{
    PendingTrackerHash _pending_hash;
public:
    BmStorageLink();
    ~BmStorageLink() override;
    bool onDown(const std::shared_ptr<storage::api::StorageMessage>& msg) override;
    bool onUp(const std::shared_ptr<storage::api::StorageMessage>& msg) override;
    void retain(uint64_t msg_id, PendingTracker &tracker) { _pending_hash.retain(msg_id, tracker); }
};

}
