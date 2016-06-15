// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/storage/distributor/bucketdb/judybucketdatabase.h>

namespace storage {

using bucketdb::DistrBucketDatabase;

namespace distributor {

BucketDatabase::Entry
JudyBucketDatabase::get(const document::BucketId& bucket) const
{
    DistrBucketDatabase::WrappedEntry wrp = _db.get(bucket, "", false);
    if (!wrp.exist()) {
        return BucketDatabase::Entry();
    } else {
        return BucketDatabase::Entry(bucket, *wrp);
    }
}

void
JudyBucketDatabase::remove(const document::BucketId& bucket)
{
    DistrBucketDatabase::WrappedEntry wrp = _db.get(bucket, "", false);
    if (wrp.exist()) {
        wrp.remove();
    }
}

void
JudyBucketDatabase::getParents(const document::BucketId& childBucket,
                               std::vector<Entry>& entries) const
{
    bucketdb::DistrBucketDatabase::EntryMap e = _db.getContained(childBucket, "");

    for (bucketdb::DistrBucketDatabase::EntryMap::iterator iter = e.begin();
         iter != e.end();
         ++iter) {
        entries.push_back(BucketDatabase::Entry(iter->first,
                                                *iter->second));
    }
}

void
JudyBucketDatabase::getAll(const document::BucketId& bucket,
                           std::vector<Entry>& entries) const
{
    bucketdb::DistrBucketDatabase::EntryMap e = _db.getAll(bucket, "");

    for (bucketdb::DistrBucketDatabase::EntryMap::iterator iter = e.begin();
         iter != e.end();
         ++iter) {
        entries.push_back(BucketDatabase::Entry(iter->first,
                                                *iter->second));
    }
}

void
JudyBucketDatabase::update(const Entry& newEntry)
{
    DistrBucketDatabase::WrappedEntry wrp = _db.get(newEntry.getBucketId(), "", true);
    (*wrp) = newEntry.getBucketInfo();
    wrp.write();
}

namespace {

class Iterator {
public:
    document::BucketId lastBucketId;
    BucketDatabase::Entry lastEntry;


    Iterator(const document::BucketId& b)
        : lastBucketId(b) {};

    DistrBucketDatabase::Decision operator()(document::BucketId::Type key,
                                             DistrBucketDatabase::Entry& info)
    {
        document::BucketId bucketId(document::BucketId::keyToBucketId(key));

        if (lastBucketId == bucketId) {
            return DistrBucketDatabase::CONTINUE;
        }

        lastEntry = BucketDatabase::Entry(bucketId, info);
        return DistrBucketDatabase::ABORT;
    }

};

}

void
JudyBucketDatabase::forEach(EntryProcessor& processor,
                            const document::BucketId& last) const
{
    document::BucketId curr = last;

    JudyBucketDatabase& mutableSelf(const_cast<JudyBucketDatabase&>(*this));
    Entry currEntry;
    while ((currEntry = mutableSelf.getNextEntry(curr)).valid()) {
        
        bool continueProcessing = processor.process(currEntry);
        if (!continueProcessing) {
            break;
        }
        curr = currEntry.getBucketId();
    }
}

BucketDatabase::Entry
JudyBucketDatabase::getNextEntry(const document::BucketId& curr)
{
    return upperBound(curr);
}

void
JudyBucketDatabase::forEach(MutableEntryProcessor& processor,
                            const document::BucketId& last)
{
    document::BucketId curr = last;

    Entry currEntry;
    while ((currEntry = getNextEntry(curr)).valid()) {

        Entry lastEntry = currEntry;
        bool continueProcessing = processor.process(currEntry);
        if (!(currEntry.getBucketInfo() == lastEntry.getBucketInfo())) {
            update(currEntry);
        }

        if (!continueProcessing) {
            break;
        }
        curr = currEntry.getBucketId();
    }
}

uint64_t
JudyBucketDatabase::size() const
{
    return _db.size();
}

void
JudyBucketDatabase::clear()
{
    _db.clear();
}

// FIXME: mutates database! No read-only functionality for this in LocakableMap!
document::BucketId
JudyBucketDatabase::getAppropriateBucket(
        uint16_t minBits,
        const document::BucketId& bid)
{
    DistrBucketDatabase::WrappedEntry wrp =
        _db.createAppropriateBucket(minBits, "", bid);
    return wrp.getBucketId();
}

uint32_t
JudyBucketDatabase::childCount(const document::BucketId&) const
{
    // Not implemented! Judy map for distributor is deprecated.
    abort();
}

BucketDatabase::Entry
JudyBucketDatabase::upperBound(const document::BucketId& value) const
{
    Iterator iter(value);
    _db.all(iter, "", value.toKey());
    return iter.lastEntry;
}

void
JudyBucketDatabase::print(std::ostream& out, bool verbose,
                          const std::string& indent) const
{
    (void) out; (void) verbose; (void) indent;
}

}
}
