// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "apply_bucket_diff_state.h"
#include "mergehandler.h"
#include <vespa/document/base/documentid.h>
#include <vespa/persistence/spi/result.h>
#include <vespa/vespalib/stllike/asciistream.h>

using storage::spi::Result;

namespace storage {

ApplyBucketDiffState::ApplyBucketDiffState(const MergeBucketInfoSyncer& merge_bucket_info_syncer, const spi::Bucket& bucket)
    : _merge_bucket_info_syncer(merge_bucket_info_syncer),
      _bucket(bucket),
      _fail_message(),
      _failed_flag(),
      _stale_bucket_info(false),
      _promise()
{
}

ApplyBucketDiffState::~ApplyBucketDiffState()
{
    try {
        sync_bucket_info();
    } catch (std::exception& e) {
        if (_fail_message.empty()) {
            _fail_message = e.what();
        }
    }
    if (_promise.has_value()) {
        _promise.value().set_value(_fail_message);
    }
}

void
ApplyBucketDiffState::on_entry_complete(std::unique_ptr<Result> result, const document::DocumentId &doc_id, const char *op)
{
    if (result->hasError() && !_failed_flag.test_and_set()) {
        vespalib::asciistream ss;
        ss << "Failed " << op
           << " for " << doc_id.toString()
           << " in " << _bucket
           << ": " << result->toString();
        _fail_message = ss.str();
    }
}

void
ApplyBucketDiffState::mark_stale_bucket_info()
{
    _stale_bucket_info = true;
}

void
ApplyBucketDiffState::sync_bucket_info()
{
    if (_stale_bucket_info) {
        _merge_bucket_info_syncer.sync_bucket_info(_bucket);
        _stale_bucket_info = false;
    }
}

std::future<vespalib::string>
ApplyBucketDiffState::get_future()
{
    _promise = std::promise<vespalib::string>();
    return _promise.value().get_future();
}

}
