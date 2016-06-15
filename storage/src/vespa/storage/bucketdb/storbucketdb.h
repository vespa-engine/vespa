// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class StorageBucketInfo
 * \ingroup bucketdb
 *
 * \brief An entry in the storage bucket database.
 *
 * \class StorBucketDatabase
 * \ingroup bucketdb
 *
 * \brief The storage bucket database.
 */
#pragma once

#include <map>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/storage/bucketdb/judymultimap.h>
#include <vespa/storage/bucketdb/lockablemap.h>
#include <vespa/storage/bucketdb/stdmapwrapper.h>
#include <vespa/storageapi/buckets/bucketinfo.h>
#include <vespa/storageapi/defs.h>

namespace storage {

namespace bucketdb {

struct StorageBucketInfo {
    api::BucketInfo info;
    unsigned disk : 8; // The disk containing the bucket

    StorageBucketInfo() : info(), disk(0xff) {}
    static bool mayContain(const StorageBucketInfo&) { return true; }
    void print(std::ostream&, bool verbose, const std::string& indent) const;
    bool valid() const { return info.valid(); }
    void setBucketInfo(const api::BucketInfo& i) { info = i; }
    const api::BucketInfo& getBucketInfo() const { return info; }
    void setEmptyWithMetaData() {
        info.setChecksum(1);
        info.setMetaCount(1);
        info.setDocumentCount(0);
        info.setTotalDocumentSize(0);
    }
    bool verifyLegal() const { return (disk != 0xff); }
    uint32_t getMetaCount() { return info.getMetaCount(); }
    void setChecksum(uint32_t crc) { info.setChecksum(crc); }
};

inline std::ostream& operator<<(std::ostream& out,
                                const StorageBucketInfo& info)
    { info.print(out, false, ""); return out; }

} // bucketdb


class StorBucketDatabase
#if __WORDSIZE == 64
    : public LockableMap<JudyMultiMap<bucketdb::StorageBucketInfo> >
#else
# warning Bucket database cannot use Judy on non-64 bit platforms
    : public LockableMap<StdMapWrapper<document::BucketId::Type, bucketdb::StorageBucketInfo> >
#endif
{
public:
    enum Flag {
        NONE = 0,
        CREATE_IF_NONEXISTING = 1,
        LOCK_IF_NONEXISTING_AND_NOT_CREATING = 2
    };
    typedef bucketdb::StorageBucketInfo Entry;

    StorBucketDatabase() {};

    void insert(const document::BucketId&, const bucketdb::StorageBucketInfo&,
                const char* clientId);

    bool erase(const document::BucketId&, const char* clientId);

    WrappedEntry get(const document::BucketId& bucket, const char* clientId,
                     Flag flags = NONE);
};

} // storage

