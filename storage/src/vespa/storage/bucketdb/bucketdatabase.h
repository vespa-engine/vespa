// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Interface for bucket database implementations in the distributor.
 */
#pragma once

#include <vespa/vespalib/util/printable.h>
#include <vespa/storage/bucketdb/bucketinfo.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/memoryusage.h>

namespace storage {

class BucketDatabase : public vespalib::Printable
{
public:
    template <typename BucketInfoType>
    class EntryBase {
        document::BucketId _bucketId;
        BucketInfoType _info;

    public:
        EntryBase() : _bucketId(0) {} // Invalid entry
        EntryBase(const document::BucketId& bId, const BucketInfoType& bucketInfo)
                : _bucketId(bId), _info(bucketInfo) {}
        EntryBase(const document::BucketId& bId, BucketInfoType&& bucketInfo)
            : _bucketId(bId), _info(std::move(bucketInfo)) {}
        explicit EntryBase(const document::BucketId& bId) : _bucketId(bId) {}

        bool operator==(const EntryBase& other) const {
            return (_bucketId == other._bucketId && _info == other._info);
        }
        bool valid() const { return (_bucketId.getRawId() != 0); }
        std::string toString() const;

        const document::BucketId& getBucketId() const { return _bucketId; }
        const BucketInfoType& getBucketInfo() const { return _info; }
        BucketInfoType& getBucketInfo() { return _info; }
        BucketInfoType* operator->() { return &_info; }
        const BucketInfoType* operator->() const { return &_info; }

        static EntryBase createInvalid() {
            return EntryBase();
        }
    };

    using Entry = EntryBase<BucketInfo>;
    // TODO avoid needing to touch memory just to get to the const entry ref
    // TODO  -> lazy ID to ConstArrayRef resolve
    using ConstEntryRef = EntryBase<ConstBucketInfoRef>;

    struct EntryProcessor {
        virtual ~EntryProcessor() = default;
        /** Return false to stop iterating. */
        virtual bool process(const ConstEntryRef& e) = 0;
    };

    ~BucketDatabase() override = default;

    virtual Entry get(const document::BucketId& bucket) const = 0;
    virtual void remove(const document::BucketId& bucket) = 0;

    /**
     * Puts all entries that may contain the given bucket id
     * into the given entry vector, including itself if found.
     */
    virtual void getParents(const document::BucketId& childBucket,
                            std::vector<Entry>& entries) const = 0;

    /**
     * Puts the sum of entries from getParents() and getChildren() into
     * the given vector.
     */
    virtual void getAll(const document::BucketId& bucket,
                        std::vector<Entry>& entries) const = 0;

    /**
     * Updates the entry for the given bucket. Adds the bucket to the bucket
     * database if it wasn't found.
     */
    virtual void update(const Entry& newEntry) = 0;

    virtual void forEach(
            EntryProcessor&,
            const document::BucketId& after = document::BucketId()) const = 0;

    /**
     * Database implementation-specific interface for appending entries
     * during a merge() operation.
     */
    struct TrailingInserter {
        virtual ~TrailingInserter() = default;
        /**
         * Insert a new database entry at the end of the current bucket space.
         *
         * Precondition: the entry's bucket ID must sort after all entries that
         * have already been iterated over or inserted via insert_at_end().
         */
        virtual void insert_at_end(const Entry&) = 0;
    };

    /**
     * Database implementation-specific interface for accessing bucket
     * entries and prepending entries during a merge() operation.
     */
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
        virtual uint64_t bucket_key() const noexcept = 0;
        virtual document::BucketId bucket_id() const noexcept = 0;
        /**
         * Returns a mutable representation of the currently iterated database
         * entry. If changes are made to this object, Result::Update must be
         * returned from merge(). Otherwise, mutation visibility is undefined.
         */
        virtual Entry& current_entry() = 0;
        /**
         * Insert a new entry into the bucket database that is ordered before the
         * currently iterated entry.
         *
         * Preconditions:
         *  - The entry's bucket ID must sort _before_ the currently iterated
         *    entry's bucket ID, in "reversed bits" bucket key order.
         *  - The entry's bucket ID must sort _after_ any entries previously
         *    inserted with insert_before_current().
         *  - The entry's bucket ID must not be the same as a bucket that was
         *    already iterated over as part of the DB merge() call or inserted
         *    via a previous call to insert_before_current().
         *    Such buckets must be handled by explicitly updating the provided
         *    entry for the iterated bucket and returning Result::Update.
         */
        virtual void insert_before_current(const Entry&) = 0;
    };

    /**
     * Interface to be implemented by callers that wish to receive callbacks
     * during a bucket merge() operation.
     */
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
        virtual Result merge(Merger&) = 0;
        /**
         * Invoked once after all existing buckets have been iterated over.
         * The provided TrailingInserter instance may be used to append
         * an arbitrary number of entries to the database.
         *
         * This is used to handle elements remaining at the end of a linear
         * merge operation.
         */
        virtual void insert_remaining_at_end(TrailingInserter&) {}
    };

    /**
     * Iterate over the bucket database in bucket key order, allowing an arbitrary
     * number of buckets to be inserted, updated and skipped in a way that is
     * optimized for the backing DB implementation.
     *
     * Merging happens in two stages:
     *  1) The MergeProcessor argument's merge() function is invoked for each existing
     *     bucket in the database. At this point new buckets ordered before the iterated
     *     bucket may be inserted and the iterated bucket may be skipped or updated.
     *  2) The MergeProcessor argument's insert_remaining_at_end() function is invoked
     *     once when all buckets have been iterated over. This enables the caller to
     *     insert new buckets that sort after the last iterated bucket.
     *
     * Changes made to the database are not guaranteed to be visible until
     * merge() returns.
     */
    virtual void merge(MergingProcessor&) = 0;

    /**
     * Get the first bucket that does _not_ compare less than or equal to
     * value in standard reverse bucket bit order (i.e. the next bucket in
     * DB iteration order after value).
     *
     * If no such bucket exists, an invalid (empty) entry should be returned.
     * If upperBound is used as part of database iteration, such a return value
     * in effect signals that the end of the database has been reached.
     */
    virtual Entry upperBound(const document::BucketId& value) const = 0;

    Entry getNext(const document::BucketId& last) const;
    
    virtual uint64_t size() const = 0;
    virtual void clear() = 0;

    // FIXME: make const as soon as Judy distributor bucket database
    // has been removed, as it has no such function and will always
    // mutate its internal database!
    virtual document::BucketId getAppropriateBucket(
            uint16_t minBits,
            const document::BucketId& bid) = 0;
    /**
     * Based on the minimum split bits and the existing buckets,
     * creates the correct new bucket in the bucket database,
     * and returns the resulting entry.
     */
    BucketDatabase::Entry createAppropriateBucket(
            uint16_t minBits,
            const document::BucketId& bid);

    virtual uint32_t childCount(const document::BucketId&) const = 0;

    struct ReadGuard {
        ReadGuard() = default;
        virtual ~ReadGuard() = default;
        
        ReadGuard(ReadGuard&&) = delete;
        ReadGuard& operator=(ReadGuard&&) = delete;
        ReadGuard(const ReadGuard&) = delete;
        ReadGuard& operator=(const ReadGuard&) = delete;

        virtual void find_parents_and_self(const document::BucketId& bucket,
                                           std::vector<Entry>& entries) const = 0;
        virtual uint64_t generation() const noexcept = 0;
    };

    virtual std::unique_ptr<ReadGuard> acquire_read_guard() const {
        return std::unique_ptr<ReadGuard>();
    }

    [[nodiscard]] virtual vespalib::MemoryUsage memory_usage() const noexcept = 0;
};

template <typename BucketInfoType>
std::ostream& operator<<(std::ostream& o, const BucketDatabase::EntryBase<BucketInfoType>& e);

} // storage
