// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/bucket.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/storage/persistence/filestorage/filestormetrics.h>
#include <vespa/vespalib/util/retain_guard.h>
#include <future>
#include <memory>
#include <optional>
#include <vector>

namespace document { class DocumentId; }
namespace storage::api { class StorageReply; }
namespace storage::spi { class Result; }

namespace storage {

struct MessageSender;
class MessageTracker;
class MergeBucketInfoSyncer;

/*
 * State of all bucket diff entry spi operation (putAsync or removeAsync)
 * for one or more ApplyBucketDiffCommand / ApplyBucketDiffReply.
 */
class ApplyBucketDiffState {
    class Deleter;
    const MergeBucketInfoSyncer&            _merge_bucket_info_syncer;
    MergeHandlerMetrics&                    _merge_handler_metrics;
    framework::MilliSecTimer                _start_time;
    spi::Bucket                             _bucket;
    vespalib::string                        _fail_message;
    std::atomic_flag                        _failed_flag;
    bool                                    _stale_bucket_info;
    std::optional<std::promise<vespalib::string>> _promise;
    std::unique_ptr<MessageTracker>         _tracker;
    std::shared_ptr<api::StorageReply>      _delayed_reply;
    MessageSender*                          _sender;
    FileStorThreadMetrics::Op*              _op_metrics;
    std::optional<framework::MilliSecTimer> _op_start_time;
    vespalib::RetainGuard                   _retain_guard;
    std::optional<framework::MilliSecTimer> _merge_start_time;

    ApplyBucketDiffState(const MergeBucketInfoSyncer &merge_bucket_info_syncer, MergeHandlerMetrics& merge_handler_metrics, const framework::Clock& clock, const spi::Bucket& bucket, vespalib::RetainGuard&& retain_guard);
public:
    static std::shared_ptr<ApplyBucketDiffState> create(const MergeBucketInfoSyncer &merge_bucket_info_syncer, MergeHandlerMetrics& merge_handler_metrics, const framework::Clock& clock, const spi::Bucket& bucket, vespalib::RetainGuard&& retain_guard);
    ~ApplyBucketDiffState();
    void on_entry_complete(std::unique_ptr<storage::spi::Result> result, const document::DocumentId &doc_id, const char *op);
    void wait();
    void check();
    void mark_stale_bucket_info();
    void sync_bucket_info();
    std::future<vespalib::string> get_future();
    void set_delayed_reply(std::unique_ptr<MessageTracker>&& tracker, std::shared_ptr<api::StorageReply>&& delayed_reply);
    void set_delayed_reply(std::unique_ptr<MessageTracker>&& tracker, MessageSender& sender, FileStorThreadMetrics::Op* op_metrics, const framework::MilliSecTimer& op_start_time, std::shared_ptr<api::StorageReply>&& delayed_reply);
    void set_tracker(std::unique_ptr<MessageTracker>&& tracker);
    void set_merge_start_time(const framework::MilliSecTimer& merge_start_time);
    const spi::Bucket& get_bucket() const noexcept { return _bucket; }
};

}
