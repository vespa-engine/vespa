// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/storage/bucketdb/distrbucketdb.h>
#include <vespa/log/log.h>
#include <vespa/storage/storageutil/utils.h>

LOG_SETUP(".distributor.bucketdb");

namespace storage {
namespace bucketdb {

void
DistrBucketDatabase::insert(const document::BucketId& bucket,
                            const BucketInfo& entry,
                            const char* clientId)
{
    bool preExisted;
#ifdef USE_JUDY
    return LockableMap<JudyMultiMap<BucketInfo> >::insert(
                bucket.toKey(), entry, clientId, preExisted);
#else
    return LockableMap<StdMapWrapper<document::BucketId::Type,
        BucketInfo> >::insert(
                bucket.toKey(), entry, clientId, preExisted);
#endif
}

DistrBucketDatabase::WrappedEntry
DistrBucketDatabase::get(const document::BucketId& bucket, const char* clientId,
                         bool createIfNonExisting)
{
#ifdef USE_JUDY
    return LockableMap<JudyMultiMap<BucketInfo> >::get(
            bucket.stripUnused().toKey(), clientId, createIfNonExisting);
#else
    return LockableMap<StdMapWrapper<document::BucketId::Type,
                                     BucketInfo> >::get(
            bucket.stripUnused().toKey(), clientId, createIfNonExisting);
#endif
}

} // storage

}
