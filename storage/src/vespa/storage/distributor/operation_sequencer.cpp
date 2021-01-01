// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operation_sequencer.h"
#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>

namespace storage::distributor {

void SequencingHandle::release() {
    if (valid()) {
        _sequencer->release(*this);
        _sequencer = nullptr;
    }
}

OperationSequencer::OperationSequencer()  = default;
OperationSequencer::~OperationSequencer() = default;

SequencingHandle OperationSequencer::try_acquire(document::BucketSpace bucket_space, const document::DocumentId& id) {
    const document::GlobalId gid(id.getGlobalId());
    if (!_active_buckets.empty()) {
        auto doc_bucket_id = gid.convertToBucketId();
        // TODO avoid O(n), but sub bucket resolving is tricky and we expect the number
        // of locked buckets to be in the range of 0 to <very small number>.
        for (const auto& entry : _active_buckets) {
            if ((entry.first.getBucketSpace() == bucket_space)
                && entry.first.getBucketId().contains(doc_bucket_id))
            {
                return SequencingHandle(SequencingHandle::BlockedByLockedBucket(entry.second));
            }
        }
    }
    const auto inserted = _active_gids.insert(gid);
    if (inserted.second) {
        return SequencingHandle(*this, gid);
    } else {
        return SequencingHandle(SequencingHandle::BlockedByPendingOperation());
    }
}

SequencingHandle OperationSequencer::try_acquire(const document::Bucket& bucket,
                                                 const vespalib::string& token) {
    const auto inserted = _active_buckets.insert(std::make_pair(bucket, token));
    if (inserted.second) {
        return SequencingHandle(*this, bucket);
    } else {
        return SequencingHandle(SequencingHandle::BlockedByLockedBucket(inserted.first->second));
    }
}

bool OperationSequencer::is_blocked(const document::Bucket& bucket) const noexcept {
    return (_active_buckets.find(bucket) != _active_buckets.end());
}

void OperationSequencer::release(const SequencingHandle& handle) {
    assert(handle.valid());
    if (handle.has_gid()) {
        _active_gids.erase(handle.gid());
    } else {
        assert(handle.has_bucket());
        _active_buckets.erase(handle.bucket());
    }
}

} // storage::distributor
