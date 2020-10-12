// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <utility>

namespace document {
class DocumentId;
}

namespace storage::distributor {

class OperationSequencer;

/**
 * Represents a move-only handle which effectively holds a guard for
 * allowing sequenced operations towards a particular document ID.
 *
 * Destroying a handle will implicitly release the guard, allowing
 * new sequenced operations towards the ID.
 */
class SequencingHandle {
    OperationSequencer* _sequencer;
    document::GlobalId _gid;
public:
    SequencingHandle() noexcept : _sequencer(nullptr) {}
    SequencingHandle(OperationSequencer& sequencer, const document::GlobalId& gid)
            : _sequencer(&sequencer),
              _gid(gid)
    {
    }

    ~SequencingHandle() {
        release();
    }

    SequencingHandle(const SequencingHandle&) = delete;
    SequencingHandle& operator=(const SequencingHandle&) = delete;

    SequencingHandle(SequencingHandle&& rhs) noexcept
            : _sequencer(rhs._sequencer),
              _gid(rhs._gid)
    {
        rhs._sequencer = nullptr;
    }

    SequencingHandle& operator=(SequencingHandle&& rhs) noexcept {
        if (&rhs != this) {
            std::swap(_sequencer, rhs._sequencer);
            std::swap(_gid, rhs._gid);
        }
        return *this;
    }

    bool valid() const noexcept { return (_sequencer != nullptr); }
    const document::GlobalId& gid() const noexcept { return _gid; }
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
    GidSet _active_gids;

    friend class SequencingHandle;
public:
    OperationSequencer();
    ~OperationSequencer();

    // Returns a handle with valid() == true iff no concurrent operations are
    // already active for `id`.
    SequencingHandle try_acquire(const document::DocumentId& id);
private:
    void release(const SequencingHandle& handle);
};

} // storage::distributor
