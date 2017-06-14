// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Interface for bucket database implementations in the distributor.
 */
#pragma once

#include <vespa/vespalib/util/printable.h>
#include <vespa/storage/bucketdb/bucketinfo.h>
#include <vespa/document/bucket/bucketid.h>

namespace storage {

class BucketDatabase : public vespalib::Printable
{
public:
    class Entry {
        document::BucketId _bucketId;
        BucketInfo _info;

    public:
        Entry() : _bucketId(0) {} // Invalid entry
        Entry(const document::BucketId& bId, const BucketInfo& bucketInfo)
            : _bucketId(bId), _info(bucketInfo) {}
        explicit Entry(const document::BucketId& bId) : _bucketId(bId) {}

        bool operator==(const Entry& other) const {
            return (_bucketId == other._bucketId && _info == other._info);
        }
        bool valid() const { return (_bucketId.getRawId() != 0); }
        std::string toString() const;

        const document::BucketId& getBucketId() const { return _bucketId; }
        const BucketInfo& getBucketInfo() const { return _info; }
        BucketInfo& getBucketInfo() { return _info; }
        BucketInfo* operator->() { return &_info; }
        const BucketInfo* operator->() const { return &_info; }

        static Entry createInvalid() {
            return Entry();
        }
    };

    template<typename T> struct Processor {
        virtual ~Processor() {}
        /** Return false to stop iterating. */
        virtual bool process(T& e) = 0;
    };
    typedef Processor<const Entry> EntryProcessor;
    typedef Processor<Entry> MutableEntryProcessor;

    virtual ~BucketDatabase() {}

    virtual Entry get(const document::BucketId& bucket) const = 0;
    virtual void remove(const document::BucketId& bucket) = 0;

    /**
     * Puts all entries that are can contain the given bucket id
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
    virtual void forEach(
            MutableEntryProcessor&,
            const document::BucketId& after = document::BucketId()) = 0;

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
};

std::ostream& operator<<(std::ostream& o, const BucketDatabase::Entry& e);

} // storage
