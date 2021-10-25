// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/bucket.h>
#include <vespa/vespalib/util/retain_guard.h>
#include <future>
#include <memory>
#include <vector>

namespace document { class DocumentId; }
namespace storage::api { class StorageReply; }
namespace storage::spi { class Result; }

namespace storage {

class ApplyBucketDiffEntryResult;
class MessageSender;
class MessageTracker;
class MergeBucketInfoSyncer;

/*
 * State of all bucket diff entry spi operation (putAsync or removeAsync)
 * for one or more ApplyBucketDiffCommand / ApplyBucketDiffReply.
 */
class ApplyBucketDiffState {
    const MergeBucketInfoSyncer&            _merge_bucket_info_syncer;
    spi::Bucket                             _bucket;
    vespalib::string                        _fail_message;
    std::atomic_flag                        _failed_flag;
    bool                                    _stale_bucket_info;
    std::optional<std::promise<vespalib::string>> _promise;
    std::unique_ptr<MessageTracker>         _tracker;
    std::shared_ptr<api::StorageReply>      _delayed_reply;
    MessageSender*                          _sender;
    vespalib::RetainGuard                   _retain_guard;

public:
    ApplyBucketDiffState(const MergeBucketInfoSyncer &merge_bucket_info_syncer, const spi::Bucket& bucket, vespalib::RetainGuard&& retain_guard);
    ~ApplyBucketDiffState();
    void on_entry_complete(std::unique_ptr<storage::spi::Result> result, const document::DocumentId &doc_id, const char *op);
    void wait();
    void check();
    void mark_stale_bucket_info();
    void sync_bucket_info();
    std::future<vespalib::string> get_future();
    void set_delayed_reply(std::unique_ptr<MessageTracker>&& tracker, std::shared_ptr<api::StorageReply>&& delayed_reply);
    void set_delayed_reply(std::unique_ptr<MessageTracker>&& tracker, MessageSender& sender, std::shared_ptr<api::StorageReply>&& delayed_reply);
};

}
