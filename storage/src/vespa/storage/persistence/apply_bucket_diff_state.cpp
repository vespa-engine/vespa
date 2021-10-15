// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "apply_bucket_diff_state.h"
#include "apply_bucket_diff_entry_result.h"
#include "mergehandler.h"

namespace storage {

ApplyBucketDiffState::ApplyBucketDiffState(const MergeBucketInfoSyncer& merge_bucket_info_syncer, const spi::Bucket& bucket)
    : _async_results(),
      _merge_bucket_info_syncer(merge_bucket_info_syncer),
      _bucket(bucket),
      _stale_bucket_info(false)
{
}

ApplyBucketDiffState::~ApplyBucketDiffState()
{
    wait();
    sync_bucket_info();
}

bool
ApplyBucketDiffState::empty() const
{
    return _async_results.empty();
}

void
ApplyBucketDiffState::wait()
{
    for (auto &result_to_check : _async_results) {
        result_to_check.wait();
    }
}

void
ApplyBucketDiffState::check()
{
    wait();
    try {
        for (auto& result_to_check : _async_results) {
            result_to_check.check_result();
        }
    } catch (std::exception&) {
        _async_results.clear();
        throw;
    }
    _async_results.clear();
}

void
ApplyBucketDiffState::push_back(ApplyBucketDiffEntryResult&& result)
{
    _async_results.push_back(std::move(result));
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

}
