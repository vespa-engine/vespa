// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "storbucketdb.h"
#include <vespa/storage/common/bucketoperationlogger.h>

namespace storage {
namespace bucketdb {

void
StorageBucketInfo::
print(std::ostream& out, bool, const std::string&) const
{
    out << info << ", disk " << disk;
}

} // bucketdb

void
StorBucketDatabase::insert(const document::BucketId& bucket,
                           const bucketdb::StorageBucketInfo& entry,
                           const char* clientId)
{
    assert(entry.disk != 0xff);
    bool preExisted;
#if __WORDSIZE == 64
    return LockableMap<JudyMultiMap<Entry> >::insert(
                bucket.toKey(), entry, clientId, preExisted);
#else
    return LockableMap<StdMapWrapper<document::BucketId::Type, Entry> >::insert(
            bucket.toKey(), entry, clientId, preExisted);
#endif
}

bool
StorBucketDatabase::erase(const document::BucketId& bucket,
                          const char* clientId)
{
#if __WORDSIZE == 64
    return LockableMap<JudyMultiMap<Entry> >::erase(
            bucket.stripUnused().toKey(), clientId);
#else
    return LockableMap<StdMapWrapper<document::BucketId::Type, Entry> >::erase(
            bucket.stripUnused().toKey(), clientId);
#endif
}

StorBucketDatabase::WrappedEntry
StorBucketDatabase::get(const document::BucketId& bucket,
                        const char* clientId,
                        Flag flags)
{
    bool createIfNonExisting = (flags & CREATE_IF_NONEXISTING);
    bool lockIfNonExisting = (flags & LOCK_IF_NONEXISTING_AND_NOT_CREATING);
#if __WORDSIZE == 64
    return LockableMap<JudyMultiMap<Entry> >::get(
                bucket.stripUnused().toKey(), clientId, createIfNonExisting,
                lockIfNonExisting);
#else
    return LockableMap<StdMapWrapper<document::BucketId::Type, Entry> >::get(
                bucket.stripUnused().toKey(), clientId,
                createIfNonExisting, lockIfNonExisting);
#endif
}


} // storage
