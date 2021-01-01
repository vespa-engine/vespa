// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Interface for bucket database implementations in the distributor.
 */
#pragma once

#include "db_merger.h"
#include "read_guard.h"
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

    /*
     * Interface class used by process_update() for updating an entry
     * with a single call to the bucket database.
     */
    struct EntryUpdateProcessor {
        virtual ~EntryUpdateProcessor() = default;
        virtual Entry create_entry(const document::BucketId& bucket) const = 0;
        /*
         * Modifies entry.
         * returns true if modified entry should be kept.
         * returns false if entry should be removed.
         */
        virtual bool process_entry(Entry &entry) const = 0;
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

    virtual void process_update(const document::BucketId& bucket, EntryUpdateProcessor &processor, bool create_if_nonexisting) = 0;

    virtual void forEach(
            EntryProcessor&,
            const document::BucketId& after = document::BucketId()) const = 0;

    using TrailingInserter = bucketdb::TrailingInserter<Entry>;
    using Merger           = bucketdb::Merger<Entry>;
    using MergingProcessor = bucketdb::MergingProcessor<Entry>;

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

    using ReadGuard = bucketdb::ReadGuard<Entry, ConstEntryRef>;

    virtual std::unique_ptr<ReadGuard> acquire_read_guard() const {
        return std::unique_ptr<bucketdb::ReadGuard<Entry, ConstEntryRef>>();
    }

    [[nodiscard]] virtual vespalib::MemoryUsage memory_usage() const noexcept = 0;
};

template <typename BucketInfoType>
std::ostream& operator<<(std::ostream& o, const BucketDatabase::EntryBase<BucketInfoType>& e);

} // storage
