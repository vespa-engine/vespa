// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/document/bucket/bucket.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <set>
#include <utility>
#include <variant>

namespace document {
class DocumentId;
}

namespace storage::distributor {

class OperationSequencer;

/**
 * Represents a move-only handle which effectively holds a guard for
 * allowing sequenced operations towards a particular document ID or
 * bucket ID.
 *
 * Destroying a handle will implicitly release the guard, allowing
 * new sequenced operations towards the ID.
 */
class SequencingHandle {
public:
    enum class BlockedBy {
        PendingOperation,
        LockedBucket
    };
private:
    OperationSequencer* _sequencer;
    using HandleVariant = std::variant<document::Bucket, document::GlobalId, BlockedBy>;
    HandleVariant       _handle;
public:
    SequencingHandle() noexcept
        : _sequencer(nullptr),
          _handle()
    {}

    explicit SequencingHandle(BlockedBy blocked_by)
        : _sequencer(nullptr),
          _handle(blocked_by)
    {}

    SequencingHandle(OperationSequencer& sequencer, const document::GlobalId& gid) noexcept
        : _sequencer(&sequencer),
          _handle(gid)
    {
    }

    SequencingHandle(OperationSequencer& sequencer, const document::Bucket& bucket)
        : _sequencer(&sequencer),
          _handle(bucket)
    {
    }

    ~SequencingHandle() {
        release();
    }

    SequencingHandle(const SequencingHandle&) = delete;
    SequencingHandle& operator=(const SequencingHandle&) = delete;

    SequencingHandle(SequencingHandle&& rhs) noexcept
        : _sequencer(rhs._sequencer),
          _handle(std::move(rhs._handle))
    {
        rhs._sequencer = nullptr;
    }

    SequencingHandle& operator=(SequencingHandle&& rhs) noexcept {
        if (&rhs != this) {
            std::swap(_sequencer, rhs._sequencer);
            std::swap(_handle, rhs._handle);
        }
        return *this;
    }

    [[nodiscard]] bool valid() const noexcept { return (_sequencer != nullptr); }
    [[nodiscard]] bool is_blocked() const noexcept {
        return std::holds_alternative<BlockedBy>(_handle);
    }
    [[nodiscard]] BlockedBy blocked_by() const noexcept {
        return std::get<BlockedBy>(_handle); // FIXME can actually throw
    }
    [[nodiscard]] bool is_blocked_by(BlockedBy blocker) const noexcept {
        return (is_blocked() && blocked_by() == blocker);
    }
    [[nodiscard]] bool has_bucket() const noexcept {
        return std::holds_alternative<document::Bucket>(_handle);
    }
    const document::Bucket& bucket() const noexcept {
        return std::get<document::Bucket>(_handle); // FIXME can actually throw
    }
    [[nodiscard]] bool has_gid() const noexcept {
        return std::holds_alternative<document::GlobalId>(_handle);
    }
    const document::GlobalId& gid() const noexcept {
        return std::get<document::GlobalId>(_handle); // FIXME can actually throw
    }
    void release();
};

/**
 * An operation sequencer allows for efficiently checking if an operation is
 * already pending for a given document ID (with very high probability; false
 * positives are possible, but false negatives are not).
 *
 * When a SequencingHandle is acquired for a given ID, no further valid handles
 * can be acquired for that ID until the original handle has been destroyed.
 */
class OperationSequencer {
    using GidSet = vespalib::hash_set<document::GlobalId, document::GlobalId::hash>;
    using BucketSet = std::set<document::Bucket>;

    GidSet    _active_gids;
    BucketSet _active_buckets;

    friend class SequencingHandle;
public:
    OperationSequencer();
    ~OperationSequencer();

    // Returns a handle with valid() == true iff no concurrent operations are
    // already active for `id` _and_ the there are no active bucket locks for
    // any bucket that may contain `id`.
    SequencingHandle try_acquire(document::BucketSpace bucket_space, const document::DocumentId& id);

    SequencingHandle try_acquire(const document::Bucket& bucket);

    bool is_blocked(const document::Bucket&) const noexcept;
private:
    void release(const SequencingHandle& handle);
};

} // storage::distributor
