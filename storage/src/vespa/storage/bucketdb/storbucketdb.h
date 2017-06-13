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

#include "judymultimap.h"
#include "lockablemap.h"
#include "stdmapwrapper.h"
#include "storagebucketinfo.h"
#include <vespa/storageapi/defs.h>

namespace storage {


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

