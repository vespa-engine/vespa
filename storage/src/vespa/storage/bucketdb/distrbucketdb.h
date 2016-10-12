// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/bucketdb/judymultimap.h>
#include <vespa/storage/bucketdb/lockablemap.h>
#include <vespa/storage/bucketdb/stdmapwrapper.h>
#include <deque>
#include <vespa/vespalib/util/printable.h>
#include <inttypes.h>
#include <map>
#include <stdexcept>
#include <vector>
#include <vespa/vespalib/util/sync.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/storage/bucketdb/bucketinfo.h>

#if __WORDSIZE == 64
  #define USE_JUDY
#endif

//#undef USE_JUDY

namespace storage {

namespace bucketdb {

class DistrBucketDatabase
#ifdef USE_JUDY
    : public LockableMap<JudyMultiMap<BucketInfo> >
#else
    : public LockableMap<StdMapWrapper<document::BucketId::Type,
                                       BucketInfo> >
#endif
{
public:
    DistrBucketDatabase() {};

    typedef BucketInfo Entry;

    void insert(const document::BucketId&,
                const BucketInfo&,
                const char* clientId);

    WrappedEntry get(const document::BucketId& bucket,
                     const char* clientId,
                     bool createIfNonExisting = false);
};

}

}


