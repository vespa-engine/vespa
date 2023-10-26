// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>

namespace storage::bucketdb {

/**
 * Database implementation-specific interface for appending entries
 * during a merge() operation.
 */
template <typename ValueT>
struct TrailingInserter {
    virtual ~TrailingInserter() = default;
    /**
     * Insert a new database entry at the end of the current bucket space.
     *
     * Precondition: the bucket ID must sort after all entries that
     * have already been iterated over or inserted via insert_at_end().
     */
    virtual void insert_at_end(const document::BucketId& bucket_id, const ValueT&) = 0;
};

/**
 * Database implementation-specific interface for accessing bucket
 * entries and prepending entries during a merge() operation.
 */
template <typename ValueT>
struct Merger {
    virtual ~Merger() = default;

    // TODO this should ideally be separated into read/write functions, but this
    // will suffice for now to avoid too many changes.

    /**
     * Bucket key/ID of the currently iterated entry. Unless the information stored
     * in the DB Entry is needed, using one of these methods should be preferred to
     * getting the bucket ID via current_entry(). The underlying DB is expected to
     * have cheap access to the ID but _may_ have expensive access to the entry itself.
     */
    [[nodiscard]] virtual uint64_t bucket_key() const noexcept = 0;
    [[nodiscard]] virtual document::BucketId bucket_id() const noexcept = 0;
    /**
     * Returns a mutable representation of the currently iterated database
     * entry. If changes are made to this object, Result::Update must be
     * returned from merge(). Otherwise, mutation visibility is undefined.
     */
    [[nodiscard]] virtual ValueT& current_entry() = 0;
    /**
     * Insert a new entry into the bucket database that is ordered before the
     * currently iterated entry.
     *
     * Preconditions:
     *  - The bucket ID must sort _before_ the currently iterated
     *    entry's bucket ID, in "reversed bits" bucket key order.
     *  - The bucket ID must sort _after_ any entries previously
     *    inserted with insert_before_current().
     *  - The bucket ID must not be the same as a bucket that was
     *    already iterated over as part of the DB merge() call or inserted
     *    via a previous call to insert_before_current().
     *    Such buckets must be handled by explicitly updating the provided
     *    entry for the iterated bucket and returning Result::Update.
     */
    virtual void insert_before_current(const document::BucketId& bucket_id, const ValueT&) = 0;
};

/**
 * Interface to be implemented by callers that wish to receive callbacks
 * during a bucket merge() operation.
 */
template <typename ValueT>
struct MergingProcessor {
    // See merge() for semantics on enum values.
    enum class Result {
        Update,
        KeepUnchanged,
        Skip
    };

    virtual ~MergingProcessor() = default;
    /**
     * Invoked for each existing bucket in the database, in bucket key order.
     * The provided Merge instance may be used to access the current entry
     * and prepend entries to the DB.
     *
     * Return value semantics:
     *  - Result::Update:
     *      when merge() returns, the changes made to the current entry will
     *      become visible in the bucket database.
     *  - Result::KeepUnchanged:
     *      when merge() returns, the entry will remain in the same state as
     *      it was when merge() was originally called.
     *  - Result::Skip:
     *      when merge() returns, the entry will no longer be part of the DB.
     *      Any entries added via insert_before_current() _will_ be present.
     *
     */
    virtual Result merge(Merger<ValueT>&) = 0;
    /**
     * Invoked once after all existing buckets have been iterated over.
     * The provided TrailingInserter instance may be used to append
     * an arbitrary number of entries to the database.
     *
     * This is used to handle elements remaining at the end of a linear
     * merge operation.
     */
    virtual void insert_remaining_at_end(TrailingInserter<ValueT>&) {}
};


}
