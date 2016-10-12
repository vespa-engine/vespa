// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/bucketdb/distrbucketdb.h>

namespace storage {
namespace distributor {

class JudyBucketDatabase : public BucketDatabase
{
public:
    virtual Entry get(const document::BucketId& bucket) const;
    virtual void remove(const document::BucketId& bucket);
    virtual void getParents(const document::BucketId& childBucket,
                            std::vector<Entry>& entries) const;
    virtual void getAll(const document::BucketId& bucket,
                        std::vector<Entry>& entries) const;
    virtual void update(const Entry& newEntry);
    virtual void forEach(EntryProcessor&,
                         const document::BucketId& after) const;
    virtual void forEach(MutableEntryProcessor&,
                         const document::BucketId& after);
    uint64_t size() const;
    void clear();

    // FIXME: remove! mutates internal database!
    document::BucketId getAppropriateBucket(
            uint16_t minBits,
            const document::BucketId& bid);

    uint32_t childCount(const document::BucketId&) const override;

    Entry upperBound(const document::BucketId& bucket) const override;

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

private:
    mutable bucketdb::DistrBucketDatabase _db;

    Entry getNextEntry(const document::BucketId& id);
};

}
}

