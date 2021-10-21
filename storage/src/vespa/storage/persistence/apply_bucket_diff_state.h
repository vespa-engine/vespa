// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/bucket.h>
#include <future>
#include <memory>
#include <vector>

namespace document { class DocumentId; }
namespace storage::spi { class Result; }

namespace storage {

class ApplyBucketDiffEntryResult;
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

public:
    ApplyBucketDiffState(const MergeBucketInfoSyncer &merge_bucket_info_syncer, const spi::Bucket& bucket);
    ~ApplyBucketDiffState();
    void on_entry_complete(std::unique_ptr<storage::spi::Result> result, const document::DocumentId &doc_id, const char *op);
    void wait();
    void check();
    void mark_stale_bucket_info();
    void sync_bucket_info();
    std::future<vespalib::string> get_future();
};

}
